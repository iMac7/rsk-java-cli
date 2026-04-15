package com.rsk.utils;

import com.rsk.commands.config.CliConfig;
import java.net.URI;
import java.net.URISyntaxException;

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
      CliConfig config,
      boolean mainnet,
      boolean testnet,
      String chain,
      String chainUrl) {
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile(
          "custom-url",
          validateCustomRpcUrl(chainUrl),
          0L,
          "NATIVE",
          "",
          "",
          ChainFeatures.defaults());
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
      CliConfig config, ChainSelection selection) {
    if (selection.chain() != null && !selection.chain().isBlank()) {
      ChainProfile custom = config.getChains().getCustom().get(selection.chain());
      if (custom == null) {
        throw new IllegalArgumentException("Unknown custom chain: " + selection.chain());
      }
      warnIfUnencryptedHttp(custom.rpcUrl());
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

  public static String explorerUrl(ChainProfile chainProfile, String value, boolean isTx) {
    String template =
        isTx ? chainProfile.explorerTxUrlTemplate() : chainProfile.explorerAddressUrlTemplate();
    if (template == null || template.isBlank()) {
      return "(explorer URL not configured)";
    }
    if (template.contains("%s")) {
      return String.format(template, value);
    }
    return template.endsWith("/") ? template + value : template + "/" + value;
  }

  public static String explorerTxUrl(ChainProfile chainProfile, String txHash) {
    return explorerUrl(chainProfile, txHash, true);
  }

  public static String explorerAddressUrl(ChainProfile chainProfile, String address) {
    return explorerUrl(chainProfile, address, false);
  }

  public static String blockscoutUrl(ChainProfile chainProfile) {
    if (chainProfile.chainId() == 30L) {
      return Constants.ROOTSTOCK_MAINNET_BLOCKSCOUT_URL;
    }
    if (chainProfile.chainId() == 31L) {
      return Constants.ROOTSTOCK_TESTNET_BLOCKSCOUT_URL;
    }
    throw new IllegalArgumentException(
        "Blockscout API is only supported on Rootstock mainnet/testnet.");
  }

  public static String blockscoutAddressUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L || chainProfile.chainId() == 31L) {
      return blockscoutUrl(chainProfile) + "/address/" + address;
    }
    return explorerAddressUrl(chainProfile, address);
  }

  public static String networkDisplayName(ChainProfile chainProfile) {
    if (chainProfile.chainId() == 30L) {
      return "Rootstock Mainnet";
    }
    if (chainProfile.chainId() == 31L) {
      return "Rootstock Testnet";
    }
    return chainProfile.name();
  }

  public static void validateChainId(ChainProfile chainProfile, String operation) {
    if (chainProfile.chainId() <= 0L) {
      throw new IllegalArgumentException(
          operation
              + " requires a positive chain ID. Custom RPC URLs currently resolve with chainId=0, "
              + "which disables EIP-155 replay protection. Configure the chain with a positive chain ID "
              + "and use --chain <name> instead of --chainurl.");
    }
  }

  private static String validateCustomRpcUrl(String chainUrl) {
    String value = chainUrl.trim();
    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme();
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
        throw new IllegalArgumentException("Custom RPC URL must use http:// or https://");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("Custom RPC URL must include a hostname");
      }
      warnIfUnencryptedHttp(uri);
      return uri.toString();
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Invalid custom RPC URL: " + chainUrl, ex);
    }
  }

  private static void warnIfUnencryptedHttp(String rpcUrl) {
    if (rpcUrl == null || rpcUrl.isBlank()) {
      return;
    }
    try {
      warnIfUnencryptedHttp(new URI(rpcUrl.trim()));
    } catch (URISyntaxException ignored) {
      // Configured custom chains may be validated elsewhere; avoid hiding the selected chain here.
    }
  }

  private static void warnIfUnencryptedHttp(URI uri) {
    if ("http".equalsIgnoreCase(uri.getScheme())) {
      System.out.println(
          Terminal.cWarn(
              "WARNING: Using unencrypted HTTP. Signed transactions will be transmitted in plaintext."));
    }
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
