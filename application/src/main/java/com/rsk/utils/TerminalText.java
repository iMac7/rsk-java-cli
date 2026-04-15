package com.rsk.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.fusesource.jansi.Ansi;

final class TerminalText {
  private static final boolean UNICODE_SYMBOLS = detectUnicodeSymbols();

  private TerminalText() {}

  static String pick(String unicodeValue, String asciiValue) {
    return UNICODE_SYMBOLS ? unicodeValue : asciiValue;
  }

  static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }

  static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  static String cMuted(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }

  static String cWarn(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).a(text).reset().toString();
  }

  static String cRule() {
    return Ansi.ansi().fgRgb(140, 140, 140).a("\u2500".repeat(40)).reset().toString();
  }

  static String cTitle(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).bold().a(text).reset().toString();
  }

  static String cSelected(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  static String cFooter(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
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
