package com.rsk.java_cli;

import com.rsk.utils.Terminal;

public class WelcomeScreen {
  private static final String RESET = "\u001b[0m";
  private static final String ORANGE = "\u001b[38;2;255;153;51m";
  private static final String SOFT_ORANGE = "\u001b[38;2;255;183;77m";
  private static final String WHITE = "\u001b[37m";
  private static final String RED = "\u001b[31m";
  private static final String BOLD = "\u001b[1m";

  private WelcomeScreen() {}

  public static void printWelcome() {
    System.out.println(BOLD + ORANGE + "\nWELCOME TO RSK JAVA CLI\n" + RESET);
    System.out.println(SOFT_ORANGE + "Usage: " + RESET + WHITE + "rsk-java-cli [options] [command]" + RESET);
    System.out.println();
    System.out.println(SOFT_ORANGE + "Options:" + RESET);
    System.out.println(ORANGE + "  -v, --version" + RESET + WHITE + "             Display the current version" + RESET);
    System.out.println(ORANGE + "  -h, --help" + RESET + WHITE + "                display help for command" + RESET);
    System.out.println();
    printMenuItem(Terminal.pick("\uD83D\uDD11 wallet          ", "wallet      "), "Wallet management");
    printMenuItem(Terminal.pick("\u2699\uFE0F  config          ", "config      "), "Config UI");
    printMenuItem(Terminal.pick("\uD83D\uDCB0 balance         ", "balance     "), "Check native balance");
    printMenuItem(Terminal.pick("\uD83D\uDE80 transfer        ", "transfer    "), "Send native transfer");
    printMenuItem(Terminal.pick("\uD83E\uDDFE tx              ", "tx          "), "Transaction status and monitoring");
    printMenuItem(Terminal.pick("\uD83D\uDD0E resolve         ", "resolve     "), "Resolve RNS names to and from addresses");
    printMenuItem(Terminal.pick("\uD83D\uDEE0\uFE0F  deploy          ", "deploy      "), "Deploy contract");
    printMenuItem(Terminal.pick("\u2705 verify          ", "verify      "), "Verify contract");
    printMenuItem(Terminal.pick("\uD83D\uDCDC contract        ", "contract    "), "Interact with contract read functions");
    printMenuItem(Terminal.pick("\uD83C\uDF09 bridge          ", "bridge      "), "Interact with RSK Bridge contract");
    printMenuItem(Terminal.pick("\uD83D\uDD58 history         ", "history     "), "History API");
    printMenuItem(Terminal.pick("\uD83E\uDDF1 transaction     ", "transaction "), "Create and send transactions");
    printMenuItem(Terminal.pick("\uD83D\uDCE6 batch-transfer  ", "batch-transfer  "), "Execute batch transfers");
    printMenuItem(Terminal.pick("\uD83E\uDDEA simulate        ", "simulate    "), "Simulate transactions");
    System.out.println();
    System.out.println(SOFT_ORANGE + "Type 'clear' to start over, 'exit' to quit." + RESET);
  }

  public static String prompt() {
    return BOLD + ORANGE + "rsk-cli> " + RESET;
  }

  public static void printError(String message) {
    System.err.println(BOLD + RED + "Error: " + message + RESET);
  }

  private static void printMenuItem(String key, String description) {
    System.out.println(BOLD + ORANGE + key + RESET + WHITE + description + RESET);
  }
}
