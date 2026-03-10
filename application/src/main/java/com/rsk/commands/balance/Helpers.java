package com.rsk.commands.balance;

import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import com.rsk.utils.Rns;
import com.rsk.utils.Storage.JsonConfigRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Optional;

public class Helpers {
  private static final BigDecimal WEI = new BigDecimal("1000000000000000000");

  private final com.rsk.commands.config.Helpers configHelpers;
  private final com.rsk.commands.wallet.Helpers walletHelpers;
  private final Rpc.RpcPort rpcPort;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers,
      Rpc.RpcPort rpcPort) {
    this.configHelpers = configHelpers;
    this.walletHelpers = walletHelpers;
    this.rpcPort = rpcPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(
        new com.rsk.commands.config.Helpers(new JsonConfigRepository(homeDir)),
        com.rsk.commands.wallet.Helpers.defaultHelpers(),
        new Rpc.Web3jRpcGateway());
  }

  public ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile(
          "custom-url", chainUrl, 0L, "NATIVE", "", "", ChainFeatures.defaults());
    }

    String chainOption = normalizeChainOption(chain);
    boolean useMainnet = mainnet;
    boolean useTestnet = testnet;
    if ("mainnet".equals(chainOption)) {
      useMainnet = true;
      useTestnet = false;
      chainOption = null;
    } else if ("testnet".equals(chainOption)) {
      useMainnet = false;
      useTestnet = true;
      chainOption = null;
    }

    return Chain.resolve(
        configHelpers.loadConfig(), new Chain.ChainSelection(useMainnet, useTestnet, chainOption));
  }

  public String resolveWalletAddress(String walletName) {
    if (walletName != null && !walletName.isBlank()) {
      return walletHelpers.requireWallet(walletName).address();
    }
    throw new IllegalArgumentException("Wallet name is required.");
  }

  public String resolveActiveWalletAddress() {
    return walletHelpers
        .activeWallet()
        .map(WalletMetadata::address)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Provide --wallet, --address, or --rns, or set an active wallet."));
  }

  public String resolveAddressInput(ChainProfile chainProfile, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Address value is required.");
    }
    String trimmed = value.trim();
    if (Rns.isHexAddress(trimmed)) {
      return trimmed;
    }
    if (!Rns.isValidRnsName(trimmed)) {
      throw new IllegalArgumentException(
          "Invalid RNS format: " + trimmed + ". Expected domain-like format, e.g. alice.rsk");
    }
    return Rns.lookup(chainProfile.rpcUrl(), trimmed)
        .orElseThrow(() -> new IllegalArgumentException("RNS name not found: " + trimmed));
  }

  public BigInteger nativeBalanceWei(ChainProfile chainProfile, String address) {
    return rpcPort.getNativeBalance(chainProfile, address);
  }

  public BigDecimal toNative(BigInteger wei) {
    return new BigDecimal(wei).divide(WEI);
  }

  private static String normalizeChainOption(String chainOption) {
    if (chainOption == null || chainOption.isBlank()) {
      return chainOption;
    }
    String normalized = chainOption.trim();
    if (normalized.startsWith("chains.custom.")) {
      return normalized.substring("chains.custom.".length());
    }
    if ("chains.mainnet".equals(normalized)) {
      return "mainnet";
    }
    if ("chains.testnet".equals(normalized)) {
      return "testnet";
    }
    return normalized;
  }
}
