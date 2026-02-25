package com.evmcli.application;

import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.model.CliConfig;

public final class ChainSelector {
  private ChainSelector() {}

  public static ChainProfile resolve(CliConfig config, ChainSelection selection) {
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

    ChainProfile mainnet = config.getChains().getMainnet();
    if (mainnet == null) {
      throw new IllegalArgumentException("Missing chains.mainnet in config");
    }
    return mainnet;
  }
}
