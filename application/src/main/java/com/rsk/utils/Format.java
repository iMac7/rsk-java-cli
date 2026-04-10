package com.rsk.utils;

import java.math.BigDecimal;

public final class Format {
  private Format() {}

  public static String formatAmount(BigDecimal amount) {
    BigDecimal stripped = amount.stripTrailingZeros();
    if (stripped.scale() < 0) {
      stripped = stripped.setScale(0);
    }
    return stripped.toPlainString();
  }

  public static boolean parseBooleanStrict(String value) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException("Invalid boolean value: " + value + ". Expected true or false.");
  }
}
