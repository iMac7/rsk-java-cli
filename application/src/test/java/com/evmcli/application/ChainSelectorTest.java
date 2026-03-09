package com.evmcli.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rsk.commands.config.CliConfig;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import org.junit.jupiter.api.Test;

class ChainSelectorTest {
  @Test
  void resolvesTestnetWhenRequested() {
    CliConfig config = new CliConfig();
    config
        .getChains()
        .setMainnet(
            new ChainProfile(
                "mainnet", "https://mainnet", 30, "RBTC", null, null, ChainFeatures.defaults()));
    config
        .getChains()
        .setTestnet(
            new ChainProfile(
                "testnet", "https://testnet", 31, "tRBTC", null, null, ChainFeatures.defaults()));

    ChainProfile selected = ChainSelector.resolve(config, new ChainSelection(false, true, null));

    assertThat(selected.name()).isEqualTo("testnet");
  }
}
