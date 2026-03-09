package com.rsk.java_cli;

import com.rsk.utils.TerminalText;

public class WelcomeScreen {
  private static final String RESET = "\u001b[0m";
  private static final String ORANGE = "\u001b[38;2;255;153;51m";
  private static final String SOFT_ORANGE = "\u001b[38;2;255;183;77m";
  private static final String WHITE = "\u001b[37m";
  private static final String BOLD = "\u001b[1m";

  private WelcomeScreen() {}

  public static void printWelcome() {
    System.out.println(BOLD + ORANGE + "\nWELCOME TO RSK JAVA CLI\n" + RESET);
    System.out.println(SOFT_ORANGE + "Usage: " + RESET + WHITE + "rsk-cli [options] [command]" + RESET);
    System.out.println();
    System.out.println(SOFT_ORANGE + "Options:" + RESET);
    System.out.println(ORANGE + "  -v, --version" + RESET + WHITE + "             Display the current version" + RESET);
    System.out.println(ORANGE + "  -h, --help" + RESET + WHITE + "                display help for command" + RESET);
    System.out.println();
    printMenuItem(TerminalText.pick("\uD83D\uDD11 wallet    ", "wallet      "), "Wallet management");
    printMenuItem(TerminalText.pick("\u2699\uFE0F config    ", "config      "), "Config UI");
    printMenuItem(TerminalText.pick("\uD83D\uDCB0 balance   ", "balance     "), "Check native balance");
    printMenuItem(TerminalText.pick("\uD83D\uDE80 transfer  ", "transfer    "), "Send native transfer");
    printMenuItem(TerminalText.pick("\uD83E\uDDFE tx        ", "tx          "), "Transaction status");
    printMenuItem(TerminalText.pick("\uD83D\uDCE1 monitor   ", "monitor     "), "Session monitoring");
    printMenuItem(TerminalText.pick("\uD83D\uDD0E resolve   ", "resolve     "), "Resolve names");
    printMenuItem(TerminalText.pick("\uD83D\uDEE0\uFE0F deploy    ", "deploy      "), "Deploy contract");
    printMenuItem(TerminalText.pick("\u2705 verify    ", "verify      "), "Verify contract");
    printMenuItem(TerminalText.pick("\uD83D\uDCDC contract  ", "contract    "), "Interactive contract mode");
    printMenuItem(TerminalText.pick("\uD83C\uDF09 bridge    ", "bridge      "), "Bridge flow");
    printMenuItem(TerminalText.pick("\u26FD gas       ", "gas         "), "Coming soon");
    printMenuItem(TerminalText.pick("\uD83D\uDD58 history   ", "history     "), "History API");
    printMenuItem(TerminalText.pick("\uD83D\uDCE6 batch-transfer", "batch-transfer"), "Coming soon");
    printMenuItem(TerminalText.pick("\uD83E\uDDF1 transaction", "transaction "), "Coming soon");
    printMenuItem(TerminalText.pick("\uD83E\uDDEA simulate  ", "simulate    "), "Coming soon");
    System.out.println();
    System.out.println(SOFT_ORANGE + "Type 'clear' to start over, 'exit' to quit." + RESET);
  }

  public static String prompt() {
    return BOLD + ORANGE + "rsk-cli> " + RESET;
  }

  private static void printMenuItem(String key, String description) {
    System.out.println(BOLD + ORANGE + key + RESET + WHITE + description + RESET);
  }
}
