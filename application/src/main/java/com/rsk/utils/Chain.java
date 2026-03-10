package com.rsk.utils;

public final class Chain {
  private Chain() {}

  public record ChainSelection(boolean mainnet, boolean testnet, String chain) {}

  public record ChainFeatures(
      boolean supportsNameResolution,
      boolean supportsBridge,
      boolean supportsContractVerification,
      boolean supportsHistoryApi) {

    public static ChainFeatures defaults() {
      return new ChainFeatures(false, false, false, false);
    }
  }

  public record ChainProfile(
      String name,
      String rpcUrl,
      long chainId,
      String nativeSymbol,
      String explorerTxUrlTemplate,
      String explorerAddressUrlTemplate,
      ChainFeatures features) {}

  public static ChainProfile resolveChain(
      com.rsk.commands.config.CliConfig config,
      boolean mainnet,
      boolean testnet,
      String chain,
      String chainUrl) {
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile("custom-url", chainUrl, 0L, "NATIVE", "", "", ChainFeatures.defaults());
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

    return resolve(config, new ChainSelection(useMainnet, useTestnet, chainOption));
  }

  public static ChainProfile resolve(
      com.rsk.commands.config.CliConfig config, ChainSelection selection) {
    if (selection.chain() != null && !selection.chain().isBlank()) {
      ChainProfile custom = config.getChains().getCustom().get(selection.chain());
      if (custom == null) {
        throw new IllegalArgumentException("Unknown custom chain: " + selection.chain());
      }
      return custom;
    }

    if (selection.testnet()) {
      ChainProfile testnet = config.getChains().getTestnet();
      if (testnet == null) {
        throw new IllegalArgumentException("Missing chains.testnet in config");
      }
      return testnet;
    }

    if (!selection.mainnet()) {
      String defaultNetwork = config.getNetwork().getDefaultNetwork();
      if ("testnet".equalsIgnoreCase(defaultNetwork)) {
        ChainProfile testnet = config.getChains().getTestnet();
        if (testnet == null) {
          throw new IllegalArgumentException("Missing chains.testnet in config");
        }
        return testnet;
      }
    }

    ChainProfile mainnet = config.getChains().getMainnet();
    if (mainnet == null) {
      throw new IllegalArgumentException("Missing chains.mainnet in config");
    }
    return mainnet;
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
