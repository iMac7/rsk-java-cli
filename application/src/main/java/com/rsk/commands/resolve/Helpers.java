package com.rsk.commands.resolve;

import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rns;
import com.rsk.utils.Storage;
import java.nio.file.Path;

public class Helpers {
  private final com.rsk.commands.config.Helpers configHelpers;

  public Helpers(com.rsk.commands.config.Helpers configHelpers) {
    this.configHelpers = configHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)));
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
        configHelpers.loadConfig(), new Chain.ChainSelection(useMainnet, useTestnet || !useMainnet, chainOption));
  }

  public String resolveName(ChainProfile chainProfile, String name) {
    if (!Rns.isValidRnsName(name)) {
      throw new IllegalArgumentException(
          "Invalid RNS format: " + name + ". Expected domain-like format, e.g. alice.rsk");
    }
    return Rns.lookup(chainProfile.rpcUrl(), name)
        .orElseThrow(() -> new IllegalArgumentException("RNS name not found: " + name));
  }

  public String reverseResolve(ChainProfile chainProfile, String address) {
    if (!Rns.isHexAddress(address)) {
      throw new IllegalArgumentException("Invalid address format for reverse resolution: " + address);
    }
    return Rns.reverseLookup(chainProfile.rpcUrl(), address)
        .orElseThrow(() -> new IllegalArgumentException("No RNS name found for: " + address));
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
