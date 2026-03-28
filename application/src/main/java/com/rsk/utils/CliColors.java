package com.rsk.utils;

import org.fusesource.jansi.Ansi;

public final class CliColors {
  private CliColors() {}

  public static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  public static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  public static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }

  public static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  public static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  public static String cMuted(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }

  public static String cWarn(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).a(text).reset().toString();
  }

  public static String cRule() {
    return Ansi.ansi()
        .fgRgb(140, 140, 140)
        .a("────────────────────────────────────────")
        .reset()
        .toString();
  }
}
