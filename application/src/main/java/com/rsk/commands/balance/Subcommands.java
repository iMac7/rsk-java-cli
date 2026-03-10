package com.rsk.commands.balance;

import com.rsk.utils.Chain.ChainProfile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final Terminal MENU_TERMINAL = createTerminal();
  private static final LineReader PROMPT_READER = createPromptReader();
  private static final String[] TOKEN_MENU_ITEMS = {"rBTC", "RIF", "USDRIF", "DoC", "Custom Token"};

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

  @Command(name = "balance", description = "Get native balance", mixinStandardHelpOptions = true)
  public static class BalanceCommand implements Callable<Integer> {
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    Target target;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    @Option(
        names = "--token",
        paramLabel = "<symbol|address>",
        description = "Token symbol (RIF, USDRIF, DoC, rBTC) or ERC20 contract address")
    String token;

    private int renderedLines;

    static class Target {
      @Option(names = "--wallet", paramLabel = "<name>", description = "Wallet name")
      String wallet;

      @Option(names = "--address", paramLabel = "<address>", description = "Address or RNS name")
      String address;

      @Option(names = "--rns", paramLabel = "<name>", description = "RNS target")
      String rns;
    }

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use chains.mainnet")
      boolean mainnet;

      @Option(names = "--testnet", description = "Use chains.testnet")
      boolean testnet;

      @Option(
          names = "--chain",
          paramLabel = "<name>",
          description = "Use config chain key, e.g. chains.custom.<name> or <name>")
      String chain;

      @Option(names = "--chainurl", paramLabel = "<url>", description = "Use an explicit RPC URL")
      String chainUrl;
    }

    @Override
    public Integer call() {
      ChainProfile chainProfile =
          HELPERS.resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      String walletName = target == null ? null : target.wallet;
      String addressInput = target == null ? null : target.address;
      String rns = target == null ? null : target.rns;
      String address =
          rns != null && !rns.isBlank()
              ? HELPERS.resolveAddressInput(chainProfile, rns)
              : walletName != null && !walletName.isBlank()
                  ? HELPERS.resolveWalletAddress(walletName)
                  : addressInput != null && !addressInput.isBlank()
                      ? HELPERS.resolveAddressInput(chainProfile, addressInput)
                      : HELPERS.resolveActiveWalletAddress();

      String selectedToken = token == null || token.isBlank() ? selectToken() : token.trim();
      if (selectedToken == null) {
        return 0;
      }

      if (isNativeToken(selectedToken)) {
        printSuccess("Select token to check balance: rBTC");
        printNativeBalance(chainProfile, address);
        return 0;
      }

      if (looksLikeAddress(selectedToken)) {
        printSuccess("Select token to check balance: Custom Token");
        printCustomTokenBalance(chainProfile, address, selectedToken);
        return 0;
      }

      if ("custom".equalsIgnoreCase(selectedToken) || "Custom Token".equalsIgnoreCase(selectedToken)) {
        printSuccess("Select token to check balance: Custom Token");
        printCustomTokenBalance(chainProfile, address, promptForCustomTokenAddress(chainProfile));
        return 0;
      }

      String normalizedToken = normalizeKnownTokenSymbol(selectedToken);
      printSuccess("Select token to check balance: " + normalizedToken);
      printKnownTokenBalance(chainProfile, address, normalizedToken);
      return 0;
    }

    private String selectToken() {
      int selectedIndex = selectMenu("Select token to check balance:", TOKEN_MENU_ITEMS);
      return selectedIndex < 0 ? null : TOKEN_MENU_ITEMS[selectedIndex];
    }

    private int selectMenu(String title, String[] menuItems) {
      renderedLines = 0;
      int selectedIndex = 0;

      if (MENU_TERMINAL == null) {
        throw new IllegalStateException("Unable to initialize balance terminal.");
      }

      try {
        Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
        NonBlockingReader reader = MENU_TERMINAL.reader();
        try {
          while (true) {
            renderMenu(title, menuItems, selectedIndex);
            int key = reader.read();
            if (key < 0) {
              continue;
            }
            if (key == 3) {
              renderedLines = 0;
              return -1;
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
                return -1;
              }
              selectedIndex = updated;
            }
          }
        } finally {
          MENU_TERMINAL.setAttributes(originalAttributes);
          MENU_TERMINAL.writer().flush();
        }
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to render balance menu.", ex);
      }
    }

    private void renderMenu(String title, String[] menuItems, int selectedIndex) {
      if (renderedLines > 0) {
        System.out.print("\u001b[" + renderedLines + "F");
      }

      int lines = 0;
      System.out.println(cInfo(title));
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

    private void printNativeBalance(ChainProfile chainProfile, String address) {
      BigInteger wei;
      try {
        wei = HELPERS.nativeBalanceWei(chainProfile, address);
      } catch (Exception ex) {
        throw new IllegalStateException(
            "Unable to fetch balance on network '" + chainProfile.name() + "'.", ex);
      }

      printSuccess("Balance retrieved successfully");
      System.out.println(cInfo("📄 Wallet Address: ") + address.toLowerCase());
      System.out.println(cInfo("🌐 Network: ") + HELPERS.networkDisplayName(chainProfile));
      System.out.println(
          cInfo("💰 Current Balance: ")
              + formatAmount(HELPERS.toNative(wei))
              + " "
              + chainProfile.nativeSymbol());
      System.out.println(cInfo("👍 Ensure that transactions are being conducted on the correct network."));
    }

    private void printKnownTokenBalance(ChainProfile chainProfile, String holderAddress, String tokenSymbol) {
      String contractAddress = HELPERS.knownTokenAddress(tokenSymbol, chainProfile);
      printTokenBalance(chainProfile, holderAddress, contractAddress);
    }

    private void printCustomTokenBalance(ChainProfile chainProfile, String holderAddress, String initialValue) {
      String contractAddress = initialValue;
      while (true) {
        try {
          String validated = HELPERS.validateTokenAddress(contractAddress);
          printTokenBalance(chainProfile, holderAddress, validated);
          return;
        } catch (IllegalArgumentException ex) {
          printError(ex.getMessage());
          contractAddress = promptForCustomTokenAddress(chainProfile);
        }
      }
    }

    private String promptForCustomTokenAddress(ChainProfile chainProfile) {
      while (true) {
        try {
          String value =
              PROMPT_READER.readLine(
                  Ansi.ansi()
                      .fg(Ansi.Color.GREEN)
                      .a("✔ ")
                      .reset()
                      .fg(Ansi.Color.WHITE)
                      .a("Enter the token address")
                      .fgRgb(140, 140, 140)
                      .a(" [0x...]")
                      .reset()
                      .a(": ")
                      .toString());
          if (value == null) {
            throw new IllegalStateException("Balance check cancelled.");
          }
          String trimmed = value.trim();
          HELPERS.validateTokenAddress(trimmed);
          return trimmed;
        } catch (UserInterruptException ex) {
          throw new IllegalStateException("Balance check cancelled.");
        } catch (IllegalArgumentException ex) {
          printError(ex.getMessage());
        }
      }
    }

    private void printTokenBalance(ChainProfile chainProfile, String holderAddress, String contractAddress) {
      Helpers.TokenBalance tokenBalance = HELPERS.tokenBalance(chainProfile, holderAddress, contractAddress);
      printSuccess("Balance retrieved successfully");
      System.out.println(cInfo("📄 Token Information:"));
      System.out.printf("         Name: %s%n", tokenBalance.name());
      System.out.printf("         Contract: %s%n", tokenBalance.contractAddress());
      System.out.printf("      👤 Holder Address: %s%n", holderAddress.toLowerCase());
      System.out.printf(
          "      💰 Balance: %s %s%n",
          formatAmount(HELPERS.tokenUnitsToDecimal(tokenBalance.balance(), tokenBalance.decimals())),
          tokenBalance.symbol());
      System.out.printf("      🌐 Network: %s%n", HELPERS.networkDisplayName(chainProfile));
      System.out.println(cInfo("👍 Ensure that transactions are being conducted on the correct network."));
    }

    private String formatAmount(BigDecimal amount) {
      BigDecimal stripped = amount.stripTrailingZeros();
      if (stripped.scale() < 0) {
        stripped = stripped.setScale(0);
      }
      return stripped.toPlainString();
    }

    private boolean isNativeToken(String value) {
      return "rbtc".equalsIgnoreCase(value);
    }

    private boolean looksLikeAddress(String value) {
      return value != null && value.startsWith("0x");
    }

    private String normalizeKnownTokenSymbol(String value) {
      if ("rif".equalsIgnoreCase(value)) {
        return "RIF";
      }
      if ("usdrif".equalsIgnoreCase(value)) {
        return "USDRIF";
      }
      if ("doc".equalsIgnoreCase(value)) {
        return "DoC";
      }
      return value;
    }

    private void printSuccess(String message) {
      System.out.println(cOk("✔ " + message));
    }

    private void printError(String message) {
      System.out.println(cError("❌ " + message));
    }
  }

  private static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  private static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  private static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  private static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  private static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }
}
