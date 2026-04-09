package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rsk.commands.config.CliConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChainTest {
  @Test
  void resolveChainUsesCustomUrlBeforeConfigResolution() {
    CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();

    Chain.ChainProfile resolved =
        Chain.resolveChain(config, true, false, "custom", "http://localhost:4444");

    assertThat(resolved.name()).isEqualTo("custom-url");
    assertThat(resolved.rpcUrl()).isEqualTo("http://localhost:4444");
    assertThat(resolved.chainId()).isZero();
  }

  @Test
  void resolveChainRejectsCustomUrlWithNonHttpScheme() {
    CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();

    assertThatThrownBy(() -> Chain.resolveChain(config, false, false, null, "file:///tmp/rpc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Custom RPC URL must use http:// or https://");
  }

  @Test
  void validateChainIdRejectsZeroChainId() {
    Chain.ChainProfile custom =
        new Chain.ChainProfile(
            "custom-url", "http://localhost:4444", 0L, "NATIVE", "", "", Chain.ChainFeatures.defaults());

    assertThatThrownBy(() -> Chain.validateChainId(custom, "Transaction submission"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires a positive chain ID")
        .hasMessageContaining("disables EIP-155 replay protection");
  }

  @Test
  void resolveChainReturnsConfiguredCustomChain() {
    CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();
    Chain.ChainProfile custom =
        new Chain.ChainProfile(
            "devnet", "http://devnet", 123L, "DEV", "", "", Chain.ChainFeatures.defaults());
    config.getChains().setCustom(Map.of("devnet", custom));

    Chain.ChainProfile resolved = Chain.resolveChain(config, false, false, "chains.custom.devnet", null);

    assertThat(resolved).isEqualTo(custom);
  }

  @Test
  void resolveChainDefaultsToConfiguredNetworkWhenFlagsAreUnset() {
    CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();
    config.getNetwork().setDefaultNetwork("testnet");

    Chain.ChainProfile resolved = Chain.resolveChain(config, false, false, null, null);

    assertThat(resolved).isEqualTo(config.getChains().getTestnet());
  }

  @Test
  void resolveChainRejectsUnknownCustomChain() {
    CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();

    assertThatThrownBy(() -> Chain.resolveChain(config, false, false, "unknown", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown custom chain: unknown");
  }
}
