package com.rsk.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class TerminalText {
  private static final boolean UNICODE_SYMBOLS = detectUnicodeSymbols();

  private TerminalText() {}

  public static String pick(String unicodeValue, String asciiValue) {
    return UNICODE_SYMBOLS ? unicodeValue : asciiValue;
  }

  private static boolean detectUnicodeSymbols() {
    String msystem = System.getenv("MSYSTEM");
    if (msystem != null && !msystem.isBlank()) {
      return false;
    }

    String shell = System.getenv("SHELL");
    if (shell != null && shell.toLowerCase().contains("bash")) {
      return false;
    }

    Charset charset = Charset.defaultCharset();
    return StandardCharsets.UTF_8.equals(charset);
  }
}
