package com.evmcli.infrastructure.storage;

import com.evmcli.domain.model.WalletMetadata;
import com.evmcli.domain.model.WalletRegistry;
import com.evmcli.domain.port.WalletPort;
import com.evmcli.domain.port.WalletUnlockPort;
import com.evmcli.infrastructure.json.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import org.web3j.utils.Numeric;

public class JsonWalletRepository implements WalletPort, WalletUnlockPort {
  private final Path registryPath;
  private final Path walletsDir;
  private final ObjectMapper objectMapper;

  public JsonWalletRepository(Path homeDir) {
    this(homeDir.resolve("wallets.json"), homeDir.resolve("wallets"), ObjectMapperFactory.create());
  }

  JsonWalletRepository(Path registryPath, Path walletsDir, ObjectMapper objectMapper) {
    this.registryPath = registryPath;
    this.walletsDir = walletsDir;
    this.objectMapper = objectMapper;
  }

  @Override
  public WalletMetadata createWallet(String name, char[] password) {
    ensureNameAvailable(name);
    try {
      ECKeyPair keyPair = Keys.createEcKeyPair();
      return storeWallet(name, keyPair, password);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to create wallet", ex);
    }
  }

  @Override
  public WalletMetadata importWallet(String name, String privateKeyHex, char[] password) {
    ensureNameAvailable(name);
    try {
      BigInteger privateKey = Numeric.toBigInt(cleanHex(privateKeyHex));
      ECKeyPair keyPair = ECKeyPair.create(privateKey);
      return storeWallet(name, keyPair, password);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid private key", ex);
    }
  }

  @Override
  public List<WalletMetadata> listWallets() {
    return new ArrayList<>(loadRegistry().getWallets()).stream()
        .sorted(Comparator.comparing(WalletMetadata::createdAt))
        .toList();
  }

  @Override
  public Optional<WalletMetadata> findByName(String name) {
    return loadRegistry().getWallets().stream().filter(w -> w.name().equals(name)).findFirst();
  }

  @Override
  public Optional<WalletMetadata> getActiveWallet() {
    WalletRegistry registry = loadRegistry();
    UUID activeId = registry.getActiveWalletId();
    if (activeId == null) {
      return Optional.empty();
    }
    return registry.getWallets().stream().filter(w -> w.walletId().equals(activeId)).findFirst();
  }

  @Override
  public void switchActiveWallet(String name) {
    WalletRegistry registry = loadRegistry();
    WalletMetadata wallet =
        registry.getWallets().stream()
            .filter(w -> w.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + name));
    registry.setActiveWalletId(wallet.walletId());
    saveRegistry(registry);
  }

  @Override
  public void renameWallet(String oldName, String newName) {
    WalletRegistry registry = loadRegistry();
    ensureNameAvailable(newName);
    List<WalletMetadata> updated =
        registry.getWallets().stream()
            .map(
                w -> {
                  if (!w.name().equals(oldName)) {
                    return w;
                  }
                  return new WalletMetadata(
                      w.walletId(),
                      newName,
                      w.address(),
                      w.keystorePath(),
                      w.createdAt(),
                      Instant.now());
                })
            .toList();
    registry.setWallets(updated);
    saveRegistry(registry);
  }

  @Override
  public void deleteWallet(String name) {
    WalletRegistry registry = loadRegistry();
    List<WalletMetadata> remaining =
        registry.getWallets().stream().filter(w -> !w.name().equals(name)).toList();
    if (remaining.size() == registry.getWallets().size()) {
      throw new IllegalArgumentException("Wallet not found: " + name);
    }
    registry.setWallets(remaining);
    if (registry.getActiveWalletId() != null
        && remaining.stream().noneMatch(w -> w.walletId().equals(registry.getActiveWalletId()))) {
      registry.setActiveWalletId(null);
    }
    saveRegistry(registry);
  }

  @Override
  public String unlockPrivateKeyHex(String walletName, char[] password) {
    WalletMetadata wallet =
        findByName(walletName)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletName));
    try {
      WalletFile walletFile =
          objectMapper.readValue(Path.of(wallet.keystorePath()).toFile(), WalletFile.class);
      ECKeyPair keyPair = Wallet.decrypt(new String(password), walletFile);
      return Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Unable to unlock wallet " + walletName, ex);
    }
  }

  private WalletMetadata storeWallet(String name, ECKeyPair keyPair, char[] password) throws Exception {
    WalletFile walletFile = Wallet.createStandard(new String(password), keyPair);
    UUID walletId = UUID.randomUUID();
    Files.createDirectories(walletsDir);
    Path keystorePath = walletsDir.resolve(walletId + ".json");
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(keystorePath.toFile(), walletFile);

    String address = "0x" + Keys.getAddress(keyPair.getPublicKey());
    WalletMetadata metadata =
        new WalletMetadata(walletId, name, address, keystorePath.toString(), Instant.now(), Instant.now());

    WalletRegistry registry = loadRegistry();
    List<WalletMetadata> wallets = new ArrayList<>(registry.getWallets());
    wallets.add(metadata);
    registry.setWallets(wallets);
    if (registry.getActiveWalletId() == null) {
      registry.setActiveWalletId(walletId);
    }
    saveRegistry(registry);
    return metadata;
  }

  private void ensureNameAvailable(String name) {
    if (findByName(name).isPresent()) {
      throw new IllegalArgumentException("Wallet name already exists: " + name);
    }
  }

  private WalletRegistry loadRegistry() {
    try {
      if (!Files.exists(registryPath)) {
        WalletRegistry registry = new WalletRegistry();
        saveRegistry(registry);
        return registry;
      }
      return objectMapper.readValue(registryPath.toFile(), WalletRegistry.class);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load wallet registry", ex);
    }
  }

  private void saveRegistry(WalletRegistry registry) {
    try {
      Files.createDirectories(registryPath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), registry);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to save wallet registry", ex);
    }
  }

  private static String cleanHex(String value) {
    return value.startsWith("0x") ? value.substring(2) : value;
  }
}
