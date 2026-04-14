package com.rsk.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

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

  public static BigInteger decimalToUnits(BigDecimal value, int decimals) {
    try {
      return value.movePointRight(decimals).toBigIntegerExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException(
          "Too many decimal places for token decimals=" + decimals + ": " + value, ex);
    }
  }

  public static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
  }
}
