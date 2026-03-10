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
    return Chain.resolveChain(configHelpers.loadConfig(), mainnet, testnet, chain, chainUrl);
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

}
