package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class FormatTest {
  @Test
  void decimalToUnitsConvertsExactDecimalAmounts() {
    assertThat(Format.decimalToUnits(new BigDecimal("1.25"), 18))
        .isEqualTo(new BigInteger("1250000000000000000"));
  }

  @Test
  void decimalToUnitsRejectsTooManyDecimalPlaces() {
    assertThatThrownBy(() -> Format.decimalToUnits(new BigDecimal("0.0000000000000000001"), 18))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Too many decimal places for token decimals=18");
  }

  @Test
  void splitCsvTrimsValuesAndDropsBlanks() {
    assertThat(Format.splitCsv(" alpha, ,beta,, gamma "))
        .containsExactly("alpha", "beta", "gamma");
  }

  @Test
  void splitCsvReturnsEmptyListForBlankInput() {
    assertThat(Format.splitCsv(" ")).isEmpty();
    assertThat(Format.splitCsv(null)).isEmpty();
  }
}
