package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class TransactionTest {
  @Test
  void toWeiConvertsRbtcAmountExactly() {
    assertThat(Transaction.toWei(new BigDecimal("1.23456789")))
        .isEqualTo(new BigInteger("1234567890000000000"));
  }

  @Test
  void toWeiRejectsAmountsMorePreciseThanWei() {
    assertThatThrownBy(() -> Transaction.toWei(new BigDecimal("0.0000000000000000001")))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void gasPriceRbtcToWeiConvertsExactGasPrice() {
    assertThat(Transaction.gasPriceRbtcToWei(new BigDecimal("0.000000042")))
        .isEqualTo(new BigInteger("42000000000"));
  }
}
