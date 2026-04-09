package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContractTest {
  private static final Chain.ChainProfile MAINNET =
      new Chain.ChainProfile("mainnet", "", 30L, "RBTC", "", "", Chain.ChainFeatures.defaults());
  private static final Chain.ChainProfile TESTNET =
      new Chain.ChainProfile("testnet", "", 31L, "tRBTC", "", "", Chain.ChainFeatures.defaults());

  @Test
  void resolveTokenAddressReturnsConfiguredAddressForKnownSymbol() {
    assertThat(Contract.resolveTokenAddress("RIF", TESTNET))
        .isEqualTo("0x19f64674d8a5b4e652319f5e239efd3bc969a1fe");
    assertThat(Contract.resolveTokenAddress("DoC", MAINNET))
        .isEqualTo("0xe700691da7B9851f2f35f8b8182c69c53ccad9db");
  }

  @Test
  void resolveTokenAddressTreatsRbtcAsNativeTransfer() {
    assertThat(Contract.resolveTokenAddress("rbtc", TESTNET)).isNull();
    assertThat(Contract.resolveTokenAddress(" ", TESTNET)).isNull();
  }

  @Test
  void resolveTokenAddressAcceptsExplicitContractAddresses() {
    assertThat(Contract.resolveTokenAddress("0x1111111111111111111111111111111111111111", TESTNET))
        .isEqualTo("0x1111111111111111111111111111111111111111");
  }

  @Test
  void resolveTokenAddressRejectsInvalidContractAddresses() {
    assertThatThrownBy(() -> Contract.resolveTokenAddress("not-an-address", TESTNET))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid token contract address");
  }
}
