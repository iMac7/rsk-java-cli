package com.rsk.java_cli;

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
    printMenuItem("🔑 wallet    ", "Wallet management");
    printMenuItem("💰 balance   ", "Coming soon");
    printMenuItem("🚀 transfer  ", "Coming soon");
    printMenuItem("🧾 tx        ", "Coming soon");
    printMenuItem("⚙️ config    ", "Config UI");
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
