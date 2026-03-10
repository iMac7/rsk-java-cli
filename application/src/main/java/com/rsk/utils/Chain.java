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
}
