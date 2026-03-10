package com.rsk.commands.config;

import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.TerminalText;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import picocli.CommandLine.Command;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final com.rsk.commands.wallet.Helpers WALLET_HELPERS =
      com.rsk.commands.wallet.Helpers.defaultHelpers();
  private static final Terminal MENU_TERMINAL = createTerminal();
  private static final LineReader PROMPT_READER = createPromptReader();
  private static final String[] MAIN_MENU_ITEMS = {
    TerminalText.pick("\uD83D\uDCCB View Current Configuration", "[view] View Current Configuration"),
    TerminalText.pick("\uD83C\uDF10 Configure Network Settings", "[network] Configure Network Settings"),
    TerminalText.pick("\u26FD Configure Gas Settings", "[gas] Configure Gas Settings"),
    TerminalText.pick("\uD83D\uDD11 Configure API Keys", "[api] Configure API Keys"),
    TerminalText.pick("\uD83C\uDFA8 Configure Display Preferences", "[display] Configure Display Preferences"),
    TerminalText.pick("\uD83D\uDC5B Configure Wallet Preferences", "[wallet] Configure Wallet Preferences"),
    TerminalText.pick("\uD83D\uDD04 Reset to Defaults", "[reset] Reset to Defaults"),
    TerminalText.pick("\uD83D\uDCBE Save and Exit", "[save] Save and Exit")
  };
  private static final String[] NETWORK_MENU_ITEMS = {
    "Mainnet", "Testnet", TerminalText.pick("\u21A9\uFE0F Back", "[back] Back")
  };
  private static final String[] API_MENU_ITEMS = {
    TerminalText.pick("\uD83D\uDD10 Set/Update API Key", "[set] Set/Update API Key"),
    TerminalText.pick("\u274C Remove API Key", "[remove] Remove API Key"),
    TerminalText.pick("\uD83D\uDCCB List API Keys", "[list] List API Keys"),
    TerminalText.pick("\u21A9\uFE0F Back", "[back] Back")
  };
  private static final int MAIN_SAVE_INDEX = MAIN_MENU_ITEMS.length - 1;
  private static final int NETWORK_BACK_INDEX = NETWORK_MENU_ITEMS.length - 1;
  private static final int API_BACK_INDEX = API_MENU_ITEMS.length - 1;

  private Subcommands() {}

  private static Terminal createTerminal() {
    try {
      return TerminalBuilder.builder().system(true).encoding(StandardCharsets.UTF_8).build();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static LineReader createPromptReader() {
    if (MENU_TERMINAL != null) {
      return LineReaderBuilder.builder().terminal(MENU_TERMINAL).build();
    }
    return LineReaderBuilder.builder().build();
  }

  @Command(name = "config", description = "Config UI", mixinStandardHelpOptions = true)
  public static class ConfigCommand implements Callable<Integer> {
    private int renderedLines;

    @Override
    public Integer call() {
      CliConfig config = HELPERS.loadConfig();
      boolean dirty = false;

      while (true) {
        try {
          int selected = selectMenu(mainTitle(config, dirty), MAIN_MENU_ITEMS, MAIN_SAVE_INDEX, "save");
          if (selected == MAIN_SAVE_INDEX) {
            HELPERS.saveConfig(config);
            printSuccess("Configuration saved.");
            return 0;
          }

          switch (selected) {
            case 0 -> viewCurrentConfiguration(config);
            case 1 -> dirty = configureNetworkSettings(config) || dirty;
            case 2 -> dirty = configureGasSettings(config) || dirty;
            case 3 -> dirty = configureApiKeys(config) || dirty;
            case 4 -> dirty = configureDisplayPreferences(config) || dirty;
            case 5 -> dirty = configureWalletPreferences(config) || dirty;
            case 6 -> {
              if (confirmReset()) {
                config = Helpers.defaultConfig();
                dirty = true;
                printSuccess("Configuration reset to defaults.");
              }
            }
            default -> throw new IllegalArgumentException("Invalid config menu selection.");
          }
        } catch (InteractiveCancelledException ignored) {
          if (!dirty || promptBoolean("Discard unsaved changes and exit?", false)) {
            return 0;
          }
        }
      }
    }

    private String mainTitle(CliConfig config, boolean dirty) {
      String title =
          TerminalText.pick(
              "\u2699\uFE0F RSK CLI Configuration Manager",
              "RSK CLI Configuration Manager");
      if (!dirty) {
        return title;
      }
      return title + "\n" + cWarn("* Unsaved changes");
    }

    private boolean configureNetworkSettings(CliConfig config) {
      int selected =
          selectMenu(
              TerminalText.pick(
                  "\uD83C\uDF10 Select default network:", "Select default network:"),
              NETWORK_MENU_ITEMS,
              NETWORK_BACK_INDEX,
              "back");
      if (selected == NETWORK_BACK_INDEX) {
        return false;
      }

      config.getNetwork().setDefaultNetwork(selected == 0 ? "mainnet" : "testnet");
      printSuccess("Network settings updated!");
      return true;
    }

    private boolean configureGasSettings(CliConfig config) {
      printSectionHeader(TerminalText.pick("\u26FD Configure Gas Settings", "Configure Gas Settings"));
      long gasLimit =
          promptLong(
              "Enter default gas limit",
              "21000",
              config.getGas().getDefaultGasLimit());
      long gasPrice =
          promptLong(
              "Enter default gas price in Gwei (0 for auto)",
              "0",
              config.getGas().getDefaultGasPriceGwei());
      config.getGas().setDefaultGasLimit(gasLimit);
      config.getGas().setDefaultGasPriceGwei(Math.max(0L, gasPrice));
      printSuccess("Gas settings updated!");
      return true;
    }

    private boolean configureApiKeys(CliConfig config) {
      while (true) {
        int selected =
            selectMenu(
                TerminalText.pick("\uD83D\uDD11 Configure API Keys", "Configure API Keys"),
                API_MENU_ITEMS,
                API_BACK_INDEX,
                "back");
        if (selected == API_BACK_INDEX) {
          return false;
        }

        switch (selected) {
          case 0 -> {
            String value =
                promptText(
                    "Enter Alchemy API key",
                    "Paste API key",
                    config.getApiKeys().getAlchemyApiKey());
            config.getApiKeys().setAlchemyApiKey(value.trim());
            printSuccess("API key updated!");
            return true;
          }
          case 1 -> {
            if (config.getApiKeys().getAlchemyApiKey().isBlank()) {
              printInfo("Alchemy API key is not set.");
              return false;
            }
            config.getApiKeys().setAlchemyApiKey("");
            printSuccess("API key removed!");
            return true;
          }
          case 2 -> {
            printApiKeys(config);
            waitForEnter("Press Enter to go back");
          }
          default -> throw new IllegalArgumentException("Invalid API menu selection.");
        }
      }
    }

    private boolean configureDisplayPreferences(CliConfig config) {
      printSectionHeader(
          TerminalText.pick("\uD83C\uDFA8 Configure Display Preferences", "Configure Display Preferences"));
      config
          .getDisplay()
          .setShowExplorerLinks(
              promptBoolean(
                  "Show explorer links in transaction output?",
                  config.getDisplay().isShowExplorerLinks()));
      config
          .getDisplay()
          .setShowGasDetails(
              promptBoolean(
                  "Show gas details in transaction output?",
                  config.getDisplay().isShowGasDetails()));
      config
          .getDisplay()
          .setShowBlockDetails(
              promptBoolean(
                  "Show block details in transaction output?",
                  config.getDisplay().isShowBlockDetails()));
      config
          .getDisplay()
          .setCompactMode(
              promptBoolean("Enable compact mode for output?", config.getDisplay().isCompactMode()));
      printSuccess("Display preferences updated!");
      return true;
    }

    private boolean configureWalletPreferences(CliConfig config) {
      printSectionHeader(
          TerminalText.pick("\uD83D\uDC5B Configure Wallet Preferences", "Configure Wallet Preferences"));
      config
          .getWallet()
          .setAutoConfirmTransactions(
              promptBoolean(
                  "Auto-confirm transactions (skip password prompt)?",
                  config.getWallet().isAutoConfirmTransactions()));
      printSuccess("Wallet preferences updated!");
      return true;
    }

    private void viewCurrentConfiguration(CliConfig config) {
      printSectionHeader(
          TerminalText.pick("\uD83D\uDCCB View Current Configuration", "View Current Configuration"));
      System.out.println(
          cInfo("🌐 Default Network: ")
              + config.getNetwork().getDefaultNetwork().toLowerCase());
      System.out.println(
          cInfo("⛽ Default Gas Limit: ")
              + String.format("%,d", config.getGas().getDefaultGasLimit()));
      System.out.println(
          cInfo("💰 Default Gas Price: ")
              + (config.getGas().getDefaultGasPriceGwei() == 0
                  ? "Auto"
                  : config.getGas().getDefaultGasPriceGwei() + " Gwei"));
      System.out.println(
          cInfo("🔑 Alchemy API Key: ")
              + (config.getApiKeys().getAlchemyApiKey().isBlank() ? "Not set" : "Set"));
      System.out.println();
      System.out.println(cInfo("🎨 Display Preferences:"));
      System.out.println(
          "  🔗 Show Explorer Links: " + yesNo(config.getDisplay().isShowExplorerLinks()));
      System.out.println("  ⛽ Show Gas Details: " + yesNo(config.getDisplay().isShowGasDetails()));
      System.out.println("  📦 Show Block Details: " + yesNo(config.getDisplay().isShowBlockDetails()));
      System.out.println("  📱 Compact Mode: " + yesNo(config.getDisplay().isCompactMode()));
      System.out.println();
      System.out.println(cInfo("👛 Wallet Preferences:"));
      System.out.println(
          "  ✅ Auto Confirm Transactions: "
              + yesNo(config.getWallet().isAutoConfirmTransactions()));
      System.out.println("  🏦 Default Wallet: " + resolveDefaultWalletLabel(config));
      System.out.println();
      waitForEnter("Press Enter to go back");
    }

    private void printApiKeys(CliConfig config) {
      printSectionHeader(TerminalText.pick("\uD83D\uDD11 API Keys", "API Keys"));
      String alchemyKey = config.getApiKeys().getAlchemyApiKey();
      System.out.println(
          cInfo("Alchemy API Key: ")
              + (alchemyKey.isBlank() ? "Not set" : maskApiKey(alchemyKey)));
      System.out.println();
    }

    private boolean confirmReset() {
      return promptBoolean("Reset configuration to defaults?", false);
    }

    private String resolveDefaultWalletLabel(CliConfig config) {
      String configuredWallet = config.getWallet().getDefaultWalletName();
      if (configuredWallet != null && !configuredWallet.isBlank()) {
        return configuredWallet;
      }

      List<WalletMetadata> wallets = WALLET_HELPERS.listWallets();
      if (wallets.size() == 1) {
        return wallets.get(0).name() + " (auto)";
      }

      return WALLET_HELPERS.activeWallet().map(WalletMetadata::name).orElse("Not set");
    }

    private String maskApiKey(String value) {
      if (value.length() <= 8) {
        return "Set";
      }
      return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private int selectMenu(String title, String[] menuItems, int cancelIndex, String escapeAction) {
      renderedLines = 0;
      int selectedIndex = 0;

      if (MENU_TERMINAL == null) {
        throw new IllegalStateException("Unable to initialize config terminal.");
      }

      try {
        Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
        NonBlockingReader reader = MENU_TERMINAL.reader();

        try {
          while (true) {
            renderMenu(title, menuItems, selectedIndex, escapeAction);
            int key = reader.read();
            if (key < 0) {
              continue;
            }
            if (key == 3) {
              renderedLines = 0;
              throw new InteractiveCancelledException();
            }
            if (key == 13 || key == 10) {
              renderedLines = 0;
              return selectedIndex;
            }
            if (isForwardCycleKey(key)) {
              selectedIndex = moveDown(selectedIndex, menuItems.length);
              continue;
            }
            if (isBackwardCycleKey(key)) {
              selectedIndex = moveUp(selectedIndex, menuItems.length);
              continue;
            }
            if (key == 224 || key == 0) {
              selectedIndex = handleWindowsArrow(reader, selectedIndex, menuItems.length);
              continue;
            }
            if (key == 27) {
              Integer updated = handleEscapeSequence(reader, selectedIndex, menuItems.length);
              if (updated == null) {
                renderedLines = 0;
                return cancelIndex;
              }
              selectedIndex = updated;
            }
          }
        } finally {
          MENU_TERMINAL.setAttributes(originalAttributes);
          MENU_TERMINAL.writer().flush();
        }
      } catch (EndOfFileException ex) {
        throw new InteractiveCancelledException();
      } catch (InteractiveCancelledException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to render config menu.", ex);
      }
    }

    private void renderMenu(String title, String[] menuItems, int selectedIndex, String escapeAction) {
      if (renderedLines > 0) {
        System.out.print("\u001b[" + renderedLines + "F");
      }

      int lines = 0;
      String[] titleLines = title.split("\\R");
      for (String titleLine : titleLines) {
        if (!titleLine.isBlank()) {
          System.out.println(cEmph(titleLine));
        } else {
          System.out.println();
        }
        lines++;
      }

      System.out.println();
      lines++;

      System.out.println(cInfo("-- Select Action --"));
      lines++;

      for (int i = 0; i < menuItems.length; i++) {
        String pointer = i == selectedIndex ? "❯ " : "  ";
        if (i == selectedIndex) {
          System.out.println(cEmph(pointer + menuItems[i]));
        } else {
          System.out.println(cPlain(pointer + menuItems[i]));
        }
        lines++;
      }

      System.out.println();
      lines++;

      System.out.println(cMuted("Up/Down navigate | Enter select | Esc/Ctrl+C " + escapeAction));
      lines++;

      renderedLines = lines;
      System.out.flush();
    }

    private Integer handleEscapeSequence(NonBlockingReader reader, int selectedIndex, int itemCount) {
      try {
        int second = reader.read(25);
        if (second == NonBlockingReader.READ_EXPIRED || second < 0) {
          return null;
        }
        if (second == '[' || second == 'O') {
          int third = reader.read(25);
          if (third == 'A') {
            return moveUp(selectedIndex, itemCount);
          }
          if (third == 'B') {
            return moveDown(selectedIndex, itemCount);
          }
        }
        return null;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to read keyboard escape sequence.", ex);
      }
    }

    private int handleWindowsArrow(NonBlockingReader reader, int selectedIndex, int itemCount) {
      try {
        int scan = reader.read(25);
        if (scan == 72) {
          return moveUp(selectedIndex, itemCount);
        }
        if (scan == 80) {
          return moveDown(selectedIndex, itemCount);
        }
        return selectedIndex;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to read keyboard scan code.", ex);
      }
    }

    private int moveUp(int selectedIndex, int itemCount) {
      return (selectedIndex - 1 + itemCount) % itemCount;
    }

    private int moveDown(int selectedIndex, int itemCount) {
      return (selectedIndex + 1) % itemCount;
    }

    private boolean isForwardCycleKey(int key) {
      return key == 9 || key == 's' || key == 'S' || key == 'j' || key == 'J';
    }

    private boolean isBackwardCycleKey(int key) {
      return key == 'w' || key == 'W' || key == 'k' || key == 'K';
    }

    private void printSectionHeader(String title) {
      System.out.println();
      System.out.println(cEmph("✔ What would you like to do? " + title));
    }

    private String promptText(String label, String placeholder, String defaultValue) {
      while (true) {
        try {
          String value = PROMPT_READER.readLine(buildPrompt(label, placeholder, defaultValue));
          if ((value == null || value.isBlank()) && defaultValue != null && !defaultValue.isBlank()) {
            return defaultValue;
          }
          if (value == null) {
            throw new InteractiveCancelledException();
          }
          return value.trim();
        } catch (UserInterruptException ex) {
          throw new InteractiveCancelledException();
        }
      }
    }

    private long promptLong(String label, String placeholder, long defaultValue) {
      while (true) {
        String raw = promptText(label, placeholder, Long.toString(defaultValue));
        try {
          return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
          printError("Please enter a valid integer.");
        }
      }
    }

    private boolean promptBoolean(String label, boolean defaultValue) {
      while (true) {
        String raw = promptText(label, "Yes/No", defaultValue ? "Yes" : "No");
        if (raw.equalsIgnoreCase("y") || raw.equalsIgnoreCase("yes")) {
          return true;
        }
        if (raw.equalsIgnoreCase("n") || raw.equalsIgnoreCase("no")) {
          return false;
        }
        printError("Please answer with Yes or No.");
      }
    }

    private void waitForEnter(String label) {
      try {
        PROMPT_READER.readLine(buildPrompt(label, "", ""));
      } catch (UserInterruptException ex) {
        throw new InteractiveCancelledException();
      }
    }

    private String buildPrompt(String label, String placeholder, String defaultValue) {
      Ansi ansi = Ansi.ansi().fg(Ansi.Color.GREEN).a("✔ ").reset().fg(Ansi.Color.WHITE).a(label);
      String hint =
          defaultValue != null && !defaultValue.isBlank() ? defaultValue : placeholder == null ? "" : placeholder;
      if (!hint.isBlank()) {
        ansi.fgRgb(140, 140, 140).a(" [" + hint + "]");
      }
      return ansi.reset().fg(Ansi.Color.WHITE).a(": ").reset().toString();
    }

    private String yesNo(boolean value) {
      return value ? "Yes" : "No";
    }

    private void printSuccess(String message) {
      System.out.println(cOk("✅ " + message));
    }

    private void printInfo(String message) {
      System.out.println(cInfo(message));
    }

    private void printError(String message) {
      System.out.println(cError(message));
    }
  }

  static class InteractiveCancelledException extends RuntimeException {
    InteractiveCancelledException() {
      super("Interactive config action cancelled.");
    }
  }

  private static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  private static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  private static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  private static String cMuted(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }

  private static String cWarn(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).a(text).reset().toString();
  }

  private static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  private static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }
}
