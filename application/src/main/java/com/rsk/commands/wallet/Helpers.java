package com.rsk.commands.wallet;

import com.rsk.utils.Storage.JsonWalletRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class Helpers {
  public record WalletMetadata(
      UUID walletId,
      String name,
      String address,
      String keystorePath,
      Instant createdAt,
      Instant lastUsedAt) {}

  public interface WalletPort {
    WalletMetadata createWallet(String name, char[] password);

    WalletMetadata importWallet(String name, String privateKeyHex, char[] password);

    List<WalletMetadata> listWallets();

    Optional<WalletMetadata> findByName(String name);

    Optional<WalletMetadata> getActiveWallet();

    void switchActiveWallet(String name);

    void renameWallet(String oldName, String newName);

    void deleteWallet(String name);
  }

  public interface WalletUnlockPort {
    String unlockPrivateKeyHex(String walletName, char[] password);
  }

  public static class WalletRegistry {
    private UUID activeWalletId;
    private List<WalletMetadata> wallets = new ArrayList<>();

    public UUID getActiveWalletId() {
      return activeWalletId;
    }

    public void setActiveWalletId(UUID activeWalletId) {
      this.activeWalletId = activeWalletId;
    }

    public List<WalletMetadata> getWallets() {
      return wallets;
    }

    public void setWallets(List<WalletMetadata> wallets) {
      this.wallets = wallets;
    }
  }

  private final AddressBookStore addressBookStore;
  private final WalletPort walletPort;
  private final WalletUnlockPort walletUnlockPort;

  public Helpers(
      WalletPort walletPort, WalletUnlockPort walletUnlockPort, AddressBookStore addressBookStore) {
    this.walletPort = walletPort;
    this.walletUnlockPort = walletUnlockPort;
    this.addressBookStore = addressBookStore;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".evm-cli");
    JsonWalletRepository walletRepository = new JsonWalletRepository(homeDir);
    AddressBookStore addressBookStore = new AddressBookStore(homeDir.resolve("wallets.json"));
    return new Helpers(walletRepository, walletRepository, addressBookStore);
  }

  public WalletMetadata createWallet(String walletName, char[] password) {
    return preserveAddressBook(() -> walletPort.createWallet(walletName, password));
  }

  public WalletMetadata importWallet(String walletName, String privateKeyHex, char[] password) {
    return preserveAddressBook(() -> walletPort.importWallet(walletName, privateKeyHex, password));
  }

  public List<WalletMetadata> listWallets() {
    return withWalletRegistryAccess(walletPort::listWallets);
  }

  public Optional<WalletMetadata> activeWallet() {
    return withWalletRegistryAccess(walletPort::getActiveWallet);
  }

  public void switchWallet(String walletName) {
    preserveAddressBook(() -> walletPort.switchActiveWallet(walletName));
  }

  public void renameWallet(String walletName, String newName) {
    preserveAddressBook(() -> walletPort.renameWallet(walletName, newName));
  }

  public void deleteWallet(String walletName) {
    preserveAddressBook(() -> walletPort.deleteWallet(walletName));
  }

  public String dumpPrivateKey(String walletName, char[] password) {
    return withWalletRegistryAccess(() -> walletUnlockPort.unlockPrivateKeyHex(walletName, password));
  }

  public WalletMetadata requireWallet(String walletName) {
    return listWallets().stream()
        .filter(wallet -> wallet.name().equals(walletName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletName));
  }

  public Path backupWallet(String walletName, String targetPathInput) {
    WalletMetadata wallet = requireWallet(walletName);
    Path targetDirectory = resolveBackupDirectory(targetPathInput);
    Path targetPath = targetDirectory.resolve(wallet.name() + "_backup.json");

    try {
      Files.copy(
          Path.of(wallet.keystorePath()), targetPath, StandardCopyOption.REPLACE_EXISTING);
      return targetPath.toAbsolutePath().normalize();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to back up wallet " + walletName, ex);
    }
  }

  public Path resolveBackupDirectory(String targetPathInput) {
    if (targetPathInput == null || targetPathInput.isBlank()) {
      throw new IllegalArgumentException("Backup path is required.");
    }

    String sanitizedPath = stripWrappingQuotes(targetPathInput.trim());
    if (!looksLikeWindowsAbsolutePath(sanitizedPath)) {
      throw new IllegalArgumentException("Backup path must be an absolute directory path.");
    }
    Path resolvedPath = Path.of(sanitizedPath).normalize();
    if (!Files.exists(resolvedPath) || !Files.isDirectory(resolvedPath)) {
      throw new IllegalArgumentException("Backup path must point to an existing directory.");
    }
    return resolvedPath;
  }

  public Map<String, String> listAddressBook() {
    return addressBookStore.listEntries();
  }

  public void addAddressBookEntry(String label, String address) {
    addressBookStore.addEntry(label, address);
  }

  public void updateAddressBookEntry(String label, String address) {
    addressBookStore.updateEntry(label, address);
  }

  public void deleteAddressBookEntry(String label) {
    addressBookStore.deleteEntry(label);
  }

  private <T> T preserveAddressBook(Supplier<T> walletAction) {
    Map<String, String> addressBook = addressBookStore.listEntries();
    boolean hadAddressBook = addressBookStore.hasAddressBook();
    if (hadAddressBook) {
      addressBookStore.removeAddressBook();
    }

    try {
      return walletAction.get();
    } finally {
      if (hadAddressBook || !addressBook.isEmpty()) {
        addressBookStore.overwrite(addressBook);
      }
    }
  }

  private void preserveAddressBook(Runnable walletAction) {
    Map<String, String> addressBook = addressBookStore.listEntries();
    boolean hadAddressBook = addressBookStore.hasAddressBook();
    if (hadAddressBook) {
      addressBookStore.removeAddressBook();
    }

    try {
      walletAction.run();
    } finally {
      if (hadAddressBook || !addressBook.isEmpty()) {
        addressBookStore.overwrite(addressBook);
      }
    }
  }

  private <T> T withWalletRegistryAccess(Supplier<T> walletAction) {
    Map<String, String> addressBook = addressBookStore.listEntries();
    boolean hadAddressBook = addressBookStore.hasAddressBook();
    if (hadAddressBook) {
      addressBookStore.removeAddressBook();
    }

    try {
      return walletAction.get();
    } finally {
      if (hadAddressBook || !addressBook.isEmpty()) {
        addressBookStore.overwrite(addressBook);
      }
    }
  }

  private static String stripWrappingQuotes(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  private static boolean looksLikeWindowsAbsolutePath(String value) {
    return value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\");
  }
}
