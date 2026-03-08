package com.rsk.commands.wallet;

import com.evmcli.domain.model.WalletMetadata;
import com.rsk.utils.TerminalText;
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final String INPUT_CANCELLED_MESSAGE = "Input cancelled.";
  private static final Terminal MENU_TERMINAL = createTerminal();
  private static final LineReader PROMPT_READER = createPromptReader();

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

  @Command(
      name = "wallet",
      description = "Wallet management",
      mixinStandardHelpOptions = true,
      subcommands = {
        Create.class,
        Import.class,
        ListCmd.class,
        Active.class,
        Dump.class,
        Switch.class,
        Rename.class,
        Backup.class,
        Delete.class
      })
  public static class WalletCommand implements Callable<Integer> {
    private static final String[] MENU_ITEMS = {
      TerminalText.pick("\uD83C\uDD95 Create a new wallet", "[new] Create a new wallet"),
      TerminalText.pick("\uD83D\uDD11 Import existing wallet", "[import] Import existing wallet"),
      TerminalText.pick("\uD83D\uDD0D List saved wallets", "[list] List saved wallets"),
      TerminalText.pick("\uD83D\uDD10 Show wallet private key", "[dump] Show wallet private key"),
      TerminalText.pick("\uD83D\uDD01 Switch wallet", "[switch] Switch wallet"),
      TerminalText.pick("\uD83D\uDCDD Update wallet name", "[rename] Update wallet name"),
      TerminalText.pick("\uD83D\uDCD2 Address book", "[book] Address book"),
      TerminalText.pick("\uD83D\uDCC2 Backup wallet data", "[backup] Backup wallet data"),
      TerminalText.pick("\u274C Delete wallet", "[delete] Delete wallet"),
      TerminalText.pick("\uD83D\uDEAA Exit", "[exit] Exit")
    };
    private static final String[] ADDRESS_BOOK_MENU_ITEMS = {
      TerminalText.pick("\u2795 Add address", "[add] Add address"),
      TerminalText.pick("\uD83D\uDCD6 View address book", "[view] View address book"),
      TerminalText.pick("\u270F\uFE0F Update address", "[update] Update address"),
      TerminalText.pick("\uD83D\uDDD1\uFE0F Delete address", "[delete] Delete address"),
      TerminalText.pick("\u21A9\uFE0F Back", "[back] Back")
    };
    private static final int EXIT_INDEX = MENU_ITEMS.length - 1;
    private static final int ADDRESS_BOOK_BACK_INDEX = ADDRESS_BOOK_MENU_ITEMS.length - 1;

    private int renderedLines = 0;

    @Override
    public Integer call() {
      while (true) {
        int selected = selectMenu("What would you like to do?", MENU_ITEMS, EXIT_INDEX);
        if (selected == EXIT_INDEX) {
          return 0;
        }

        System.out.println();
        System.out.println(
            Ansi.ansi().fgRgb(255, 183, 77).bold().a("Selected: ").a(MENU_ITEMS[selected]).reset());
        System.out.println();

        try {
          executeAction(selected);
        } catch (InteractiveCancelledException ex) {
          System.out.println(INPUT_CANCELLED_MESSAGE);
        }

        System.out.println();
        System.out.println();
      }
    }

    private int selectMenu(String title, String[] menuItems, int cancelIndex) {
      renderedLines = 0;
      int selectedIndex = 0;

      if (MENU_TERMINAL == null) {
        throw new IllegalStateException("Unable to initialize wallet terminal.");
      }

      try {
        Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
        NonBlockingReader reader = MENU_TERMINAL.reader();

        try {
          while (true) {
            renderMenu(title, menuItems, selectedIndex, cancelIndex >= 0 ? "exit" : "back");
            int key = reader.read();
            if (key < 0) {
              continue;
            }
            if (key == 3) {
              renderedLines = 0;
              return cancelIndex;
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
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to render wallet menu.", ex);
      }
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

    private void renderMenu(String title, String[] menuItems, int selectedIndex, String escapeAction) {
      if (renderedLines > 0) {
        System.out.print("\u001b[" + renderedLines + "F");
      }

      int lines = 0;

      System.out.println(Ansi.ansi().fgRgb(255, 183, 77).bold().a(title).reset());
      lines++;

      System.out.println();
      lines++;

      for (int i = 0; i < menuItems.length; i++) {
        String pointer = i == selectedIndex ? "> " : "  ";
        if (i == selectedIndex) {
          System.out.println(
              Ansi.ansi().fgRgb(255, 153, 51).bold().a(pointer + menuItems[i]).reset());
        } else {
          System.out.println(Ansi.ansi().fg(Ansi.Color.WHITE).a(pointer + menuItems[i]).reset());
        }
        lines++;
      }

      System.out.println();
      lines++;

      System.out.println(
          Ansi.ansi()
              .fgRgb(255, 183, 77)
              .a("Up/Down navigate | Enter select | Ctrl+C/Esc " + escapeAction)
              .reset());
      lines++;

      renderedLines = lines;
      System.out.flush();
    }

    private void executeAction(int selectedIndex) {
      switch (selectedIndex) {
        case 0 -> runCreateFlow();
        case 1 -> runImportFlow();
        case 2 -> new ListCmd().call();
        case 3 -> runDumpFlow();
        case 4 -> runSwitchFlow();
        case 5 -> runRenameFlow();
        case 6 -> runAddressBookFlow();
        case 7 -> runBackupFlow();
        case 8 -> runDeleteFlow();
        case 9 -> {
          return;
        }
        default -> throw new IllegalArgumentException("Invalid wallet menu selection.");
      }
    }

    private void runCreateFlow() {
      String walletName = readRequiredText("Wallet name");
      char[] password = readPassword("Wallet password: ");
      WalletMetadata wallet = HELPERS.createWallet(walletName, password);
      System.out.printf("Created wallet %s (%s)%n", cEmph(wallet.name()), wallet.address());
    }

    private void runImportFlow() {
      String walletName = readRequiredText("Wallet name");
      String privateKey = readRequiredText("Private key (hex)");
      char[] password = readPassword("Wallet password: ");
      WalletMetadata wallet = HELPERS.importWallet(walletName, privateKey, password);
      System.out.printf("Imported wallet %s (%s)%n", cEmph(wallet.name()), wallet.address());
    }

    private void runSwitchFlow() {
      printWalletChoices();
      String walletName = readRequiredText("Wallet name");
      HELPERS.switchWallet(walletName);
      System.out.println("Active wallet switched to " + cEmph(walletName));
    }

    private void runDumpFlow() {
      printCurrentWallet();
      printWalletChoices();
      String selectedWallet = readRequiredText("Wallet name");
      char[] password = readPassword("Wallet password: ");
      String privateKey = HELPERS.dumpPrivateKey(selectedWallet, password);
      System.out.println(privateKey);
    }

    private void runRenameFlow() {
      String walletName = readRequiredText("Current wallet name");
      String newName = readRequiredText("New wallet name");
      HELPERS.renameWallet(walletName, newName);
      System.out.printf("Renamed wallet %s -> %s%n", cEmph(walletName), cEmph(newName));
    }

    private void runDeleteFlow() {
      printWalletChoices();
      String walletName = readRequiredText("Wallet name");
      String confirm = readRequiredText("Type DELETE to confirm");
      if (!"DELETE".equals(confirm)) {
        System.out.println("Delete cancelled.");
        return;
      }
      HELPERS.deleteWallet(walletName);
      System.out.println("Deleted wallet " + cEmph(walletName));
    }

    private void runBackupFlow() {
      printCurrentWallet();
      printWalletChoices();
      String walletName = readRequiredText("Wallet name");
      String backupPathInput =
          readRequiredText("Enter an absolute directory path where you want to save the backup");
      Path savedPath = HELPERS.backupWallet(walletName, backupPathInput);
      System.out.println("Changes saved at " + savedPath);
      System.out.println("Wallet backup created successfully!");
      System.out.println("Backup saved successfully at: " + savedPath);
    }

    private void runAddressBookFlow() {
      while (true) {
        int selected = selectMenu("Address book", ADDRESS_BOOK_MENU_ITEMS, ADDRESS_BOOK_BACK_INDEX);
        if (selected == ADDRESS_BOOK_BACK_INDEX) {
          return;
        }

        System.out.println();

        try {
          executeAddressBookAction(selected);
        } catch (InteractiveCancelledException ex) {
          System.out.println(INPUT_CANCELLED_MESSAGE);
        }

        System.out.println();
        System.out.println();
      }
    }

    private void executeAddressBookAction(int selectedIndex) {
      switch (selectedIndex) {
        case 0 -> runAddAddressFlow();
        case 1 -> runViewAddressBookFlow();
        case 2 -> runUpdateAddressFlow();
        case 3 -> runDeleteAddressFlow();
        case 4 -> {
          return;
        }
        default -> throw new IllegalArgumentException("Invalid address book menu selection.");
      }
    }

    private void runAddAddressFlow() {
      String label = readRequiredText("Address label");
      String address = readRequiredText("Address");
      HELPERS.addAddressBookEntry(label, address);
      System.out.printf("Saved address %s -> %s%n", cEmph(label), address);
    }

    private void runViewAddressBookFlow() {
      Map<String, String> addressBook = HELPERS.listAddressBook();
      if (addressBook.isEmpty()) {
        System.out.println("Address book is empty.");
        return;
      }

      addressBook.forEach((label, address) -> System.out.printf("%s: %s%n", cEmph(label), address));
    }

    private void runUpdateAddressFlow() {
      String label = selectAddressBookLabel("Select address to update");
      if (label == null) {
        return;
      }

      String newAddress = readRequiredText("New address");
      HELPERS.updateAddressBookEntry(label, newAddress);
      System.out.printf("Updated address %s -> %s%n", cEmph(label), newAddress);
    }

    private void runDeleteAddressFlow() {
      String label = selectAddressBookLabel("Select address to delete");
      if (label == null) {
        return;
      }

      HELPERS.deleteAddressBookEntry(label);
      System.out.println("Deleted address " + cEmph(label));
    }

    private String selectAddressBookLabel(String title) {
      Map<String, String> addressBook = HELPERS.listAddressBook();
      if (addressBook.isEmpty()) {
        System.out.println("Address book is empty.");
        return null;
      }

      String[] labels = addressBook.keySet().toArray(String[]::new);
      int selectedIndex = selectMenu(title, labels, -1);
      if (selectedIndex < 0) {
        return null;
      }
      return labels[selectedIndex];
    }

    private void printWalletChoices() {
      List<WalletMetadata> wallets = HELPERS.listWallets();
      if (wallets.isEmpty()) {
        throw new IllegalArgumentException("No wallets found.");
      }

      String active = HELPERS.activeWallet().map(WalletMetadata::name).orElse("");
      String activeMarker = TerminalText.pick("\u2B50", "*");
      String inactiveMarker = TerminalText.pick("\u2022", "-");
      System.out.println("Available wallets:");
      wallets.forEach(
          wallet -> {
            String marker = wallet.name().equals(active) ? activeMarker : inactiveMarker;
            System.out.printf("%s %s %s%n", marker, cEmph(wallet.name()), wallet.address());
          });
      System.out.println();
    }

    private void printCurrentWallet() {
      HELPERS
          .activeWallet()
          .ifPresent(wallet -> System.out.println("Current wallet: " + cEmph(wallet.name())));
    }
  }

  static class InteractiveCancelledException extends RuntimeException {
    InteractiveCancelledException() {
      super("Interactive action cancelled.");
    }
  }

  @Command(name = "create", description = "Create wallet", mixinStandardHelpOptions = true)
  static class Create implements Callable<Integer> {
    @Option(
        names = "--wallet",
        required = true,
        paramLabel = "<name>",
        description = "Wallet name")
    String walletName;

    @Override
    public Integer call() {
      char[] password = readPassword("Wallet password: ");
      WalletMetadata wallet = HELPERS.createWallet(walletName, password);
      System.out.printf("Created wallet %s (%s)%n", cEmph(wallet.name()), wallet.address());
      return 0;
    }
  }

  @Command(
      name = "import",
      description = "Import wallet from private key",
      mixinStandardHelpOptions = true)
  static class Import implements Callable<Integer> {
    @Option(
        names = "--wallet",
        required = true,
        paramLabel = "<name>",
        description = "Wallet name")
    String walletName;

    @Option(names = "--privkey", required = true, description = "Hex private key")
    String privateKey;

    @Override
    public Integer call() {
      char[] password = readPassword("Wallet password: ");
      WalletMetadata wallet = HELPERS.importWallet(walletName, privateKey, password);
      System.out.printf("Imported wallet %s (%s)%n", cEmph(wallet.name()), wallet.address());
      return 0;
    }
  }

  @Command(name = "list", description = "List wallets", mixinStandardHelpOptions = true)
  static class ListCmd implements Callable<Integer> {
    @Override
    public Integer call() {
      List<WalletMetadata> wallets = HELPERS.listWallets();
      if (wallets.isEmpty()) {
        System.out.println("No wallets found.");
        return 0;
      }
      String active = HELPERS.activeWallet().map(WalletMetadata::name).orElse("");
      wallets.forEach(
          wallet -> {
            String marker = wallet.name().equals(active) ? "*" : " ";
            System.out.printf("%s %s %s%n", marker, cEmph(wallet.name()), wallet.address());
          });
      return 0;
    }
  }

  @Command(name = "active", description = "Show active wallet", mixinStandardHelpOptions = true)
  static class Active implements Callable<Integer> {
    @Override
    public Integer call() {
      HELPERS
          .activeWallet()
          .ifPresentOrElse(
              wallet -> System.out.printf("%s %s%n", cEmph(wallet.name()), wallet.address()),
              () -> System.out.println("No active wallet."));
      return 0;
    }
  }

  @Command(name = "dump", description = "Reveal wallet private key", mixinStandardHelpOptions = true)
  static class Dump implements Callable<Integer> {
    @Option(names = "--wallet", paramLabel = "<name>", description = "Wallet name")
    String walletName;

    @Override
    public Integer call() {
      String selectedWallet = walletName;
      if (selectedWallet == null || selectedWallet.isBlank()) {
        selectedWallet =
            HELPERS
                .activeWallet()
                .map(WalletMetadata::name)
                .orElseThrow(
                    () -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
      }
      char[] password = readPassword("Wallet password: ");
      String privateKey = HELPERS.dumpPrivateKey(selectedWallet, password);
      System.out.println(privateKey);
      return 0;
    }
  }

  @Command(name = "switch", description = "Switch active wallet", mixinStandardHelpOptions = true)
  static class Switch implements Callable<Integer> {
    @Option(names = "--wallet", required = true, paramLabel = "<name>", description = "Wallet name")
    String walletName;

    @Override
    public Integer call() {
      HELPERS.switchWallet(walletName);
      System.out.println("Active wallet switched to " + cEmph(walletName));
      return 0;
    }
  }

  @Command(name = "rename", description = "Rename wallet", mixinStandardHelpOptions = true)
  static class Rename implements Callable<Integer> {
    @Option(
        names = "--wallet",
        required = true,
        paramLabel = "<name>",
        description = "Current wallet name")
    String walletName;

    @Option(names = "--to", required = true, paramLabel = "<name>", description = "New wallet name")
    String newName;

    @Override
    public Integer call() {
      HELPERS.renameWallet(walletName, newName);
      System.out.printf("Renamed wallet %s -> %s%n", cEmph(walletName), cEmph(newName));
      return 0;
    }
  }

  @Command(name = "backup", description = "Backup wallet data", mixinStandardHelpOptions = true)
  static class Backup implements Callable<Integer> {
    @Override
    public Integer call() {
      WalletCommand walletCommand = new WalletCommand();
      walletCommand.runBackupFlow();
      return 0;
    }
  }

  @Command(name = "delete", description = "Delete wallet", mixinStandardHelpOptions = true)
  static class Delete implements Callable<Integer> {
    @Option(names = "--wallet", required = true, paramLabel = "<name>", description = "Wallet name")
    String walletName;

    @Override
    public Integer call() {
      HELPERS.deleteWallet(walletName);
      System.out.println("Deleted wallet " + cEmph(walletName));
      return 0;
    }
  }

  static char[] readPassword(String prompt) {
    while (true) {
      try {
        Console console = System.console();
        if (console != null) {
          char[] password = console.readPassword(prompt);
          if (password == null) {
            throw new InteractiveCancelledException();
          }
          if (password.length == 0) {
            System.out.println("Password is required.");
            continue;
          }
          return password;
        }
        String password = PROMPT_READER.readLine(prompt, '*');
        if (password == null) {
          throw new InteractiveCancelledException();
        }
        if (password.isBlank()) {
          System.out.println("Password is required.");
          continue;
        }
        return password.toCharArray();
      } catch (UserInterruptException ex) {
        throw new InteractiveCancelledException();
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          throw new InteractiveCancelledException();
        }
        throw ex;
      }
    }
  }

  private static String readRequiredText(String label) {
    while (true) {
      String value;
      try {
        value = PROMPT_READER.readLine(label + ": ");
      } catch (UserInterruptException ex) {
        throw new InteractiveCancelledException();
      }
      if (value == null) {
        throw new InteractiveCancelledException();
      }
      if (!value.isBlank()) {
        return value.trim();
      }
      System.out.println("Value is required.");
    }
  }

  private static String cEmph(String value) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(value).reset().toString();
  }
}
