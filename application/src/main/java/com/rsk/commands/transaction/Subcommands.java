package com.rsk.commands.transaction;

import com.rsk.commands.transfer.Helpers;
import com.rsk.java_cli.WelcomeScreen;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Contract;
import com.rsk.utils.Loader;
import com.rsk.utils.Transaction;
import java.io.Console;
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
  private static final com.rsk.commands.balance.Helpers BALANCE_HELPERS =
      com.rsk.commands.balance.Helpers.defaultHelpers();
  private static final Terminal MENU_TERMINAL = createTerminal();
  private static final LineReader READER = createReader();
  private static final String[] TX_TYPES = {
    "Simple Transfer (RBTC or Token)",
    "Advanced Transfer (with custom gas settings)",
    "Raw Transaction (with custom data)",
    "Back"
  };

  private Subcommands() {}

  private static Terminal createTerminal() {
    try {
      return TerminalBuilder.builder().system(true).encoding(StandardCharsets.UTF_8).build();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static LineReader createReader() {
    if (MENU_TERMINAL != null) {
      return LineReaderBuilder.builder().terminal(MENU_TERMINAL).build();
    }
    return LineReaderBuilder.builder().build();
  }

  @Command(
      name = "transaction",
      description = "Create and send transactions",
      mixinStandardHelpOptions = true)
  public static class TransactionCommand implements Callable<Integer> {
    @Option(names = "--wallet", paramLabel = "<wallet>", description = "Name of the wallet")
    String walletName;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    private int renderedLines;

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use mainnet")
      boolean mainnet;

      @Option(names = {"-t", "--testnet"}, description = "Use testnet")
      boolean testnet;

      @Option(names = "--chain", paramLabel = "<name>", description = "Use configured chain")
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
      String selectedWallet = HELPERS.resolveWalletName(walletName);
      String walletAddress = HELPERS.walletAddress(selectedWallet);

      System.out.println(cInfo("📊 Network: ") + Transaction.networkDisplayName(chainProfile));

      while (true) {
        Integer selectedType = selectTransactionType();
        if (selectedType == null || selectedType == TX_TYPES.length - 1) {
          redrawWelcome();
          return 0;
        }

        try {
          TransactionInput input = collectTransactionInput(chainProfile, selectedType);
          printTransactionPreview(chainProfile, walletAddress, input);
          char[] password = promptPassword();
          Helpers.PendingTransfer pendingTransfer =
              Loader.runWithSpinner(
                  "⏳ Preparing transaction...",
                  () -> submit(chainProfile, selectedWallet, password, input));
          System.out.println(cOk("✔ ✅ Transaction sent"));
          System.out.println(cInfo("📊 Transaction Hash: ") + pendingTransfer.txHash());
          var receipt =
              Loader.runWithSpinner(
                  "⏳ Waiting for confirmation...",
                  () ->
                      Transaction.waitForSuccessfulReceipt(
                          chainProfile, pendingTransfer.txHash(), 120, 2000L));
          System.out.println(cOk("✅ Transaction confirmed successfully!"));
          System.out.println(cInfo("📊 Block Number: ") + receipt.getBlockNumber());
          System.out.println(cInfo("📊 Gas Used: ") + receipt.getGasUsed());
          System.out.println(
              cInfo("📊 View on Explorer: ")
                  + Transaction.explorerTxUrl(chainProfile, pendingTransfer.txHash()));
        } catch (PromptCancelledException ex) {
          System.out.println(cError("❌ Error during transaction, please check the transaction details."));
          System.out.println(cError("❌ Error details: User force closed the prompt with SIGINT"));
        } catch (Exception ex) {
          System.out.println(cError("❌ Error during transaction, please check the transaction details."));
          System.out.println(cError("❌ Error details: " + rootMessage(ex)));
          if (rootMessage(ex).toLowerCase().contains("gas")) {
            System.out.println(cWarn("⚠️  Tip: Try increasing the gas limit or gas price."));
          }
        }
      }
    }

    private Helpers.PendingTransfer submit(
        ChainProfile chainProfile, String walletName, char[] password, TransactionInput input) {
      BigInteger gasPriceWei =
          input.gasPriceRbtc() == null
              ? Transaction.defaultGasPriceWei(chainProfile)
              : Transaction.gasPriceRbtcToWei(input.gasPriceRbtc());
      if (input.tokenAddress() == null) {
        return HELPERS.sendNative(
            chainProfile,
            walletName,
            password,
            input.recipient(),
            Transaction.toWei(input.amount()),
            input.gasLimit() == null ? Transaction.defaultGasLimit() : input.gasLimit(),
            gasPriceWei,
            input.data());
      }
      return HELPERS.sendToken(
          chainProfile,
          walletName,
          password,
          input.tokenAddress(),
          input.recipient(),
          input.amount(),
          input.gasLimit(),
          gasPriceWei,
          input.data());
    }

    private TransactionInput collectTransactionInput(ChainProfile chainProfile, int selectedType) {
      String recipient = resolveRecipient(chainProfile, promptRequired("🎯 Enter recipient address"));
      boolean tokenTransfer = promptYesNo("🪙 Is this a token transfer?", false);
      String tokenAddress = null;
      if (tokenTransfer) {
        while (true) {
          try {
            tokenAddress =
                HELPERS.resolveTokenAddress(
                    chainProfile, promptRequired("📝 Enter token contract address"));
            if (tokenAddress == null) {
              throw new IllegalArgumentException("Invalid token contract address");
            }
            Contract.readTokenMetadata(chainProfile, tokenAddress);
            break;
          } catch (Exception ex) {
            System.out.println(cError("❌ " + rootMessage(ex)));
          }
        }
      }

      BigDecimal amount =
          promptAmount(tokenTransfer ? "💰 Enter token amount" : "💰 Enter amount in RBTC");

      BigInteger gasLimit = null;
      BigDecimal gasPrice = null;
      String data = "";

      if (selectedType >= 1) {
        gasLimit = promptOptionalInteger("⛽ Enter gas limit (optional)");
        gasPrice = promptOptionalDecimal("💰 Enter max fee per gas in RBTC (optional)");
        promptOptionalDecimal("💰 Enter max priority fee per gas in RBTC (optional)");
      }
      if (selectedType == 2) {
        data = promptOptionalText("📝 Enter transaction data (hex)");
      }

      return new TransactionInput(recipient, tokenAddress, amount, gasLimit, gasPrice, data);
    }

    private void printTransactionPreview(
        ChainProfile chainProfile, String walletAddress, TransactionInput input) {
      BigDecimal currentGasPriceRbtc =
          new BigDecimal(Transaction.defaultGasPriceWei(chainProfile)).movePointLeft(18);
      BigDecimal balance = HELPERS.nativeBalance(chainProfile, walletAddress);
      BigInteger estimatedGas =
          input.gasLimit() == null
              ? (input.tokenAddress() == null
                  ? Transaction.defaultGasLimit()
                  : BigInteger.valueOf(100_000L))
              : input.gasLimit();
      BigDecimal gasPriceRbtc =
          input.gasPriceRbtc() == null ? currentGasPriceRbtc : input.gasPriceRbtc();
      BigDecimal gasCost = new BigDecimal(estimatedGas).multiply(gasPriceRbtc);
      BigDecimal value = input.amount();
      BigDecimal totalCost = input.tokenAddress() == null ? gasCost.add(value) : gasCost;

      System.out.println(cInfo("📊 Current Gas Price: ") + currentGasPriceRbtc.toPlainString() + " RBTC");
      System.out.println(cInfo("📊 Checking balance for address: ") + walletAddress);
      System.out.println(
          cInfo("📊 Wallet Balance: ") + balance.toPlainString() + " " + chainProfile.nativeSymbol());
      System.out.println(cInfo("📊 Network RPC: ") + Transaction.networkDisplayName(chainProfile));
      System.out.println(
          cInfo("📊 " + (input.tokenAddress() == null ? "RBTC Transfer Details:" : "Token Transfer Details:")));
      System.out.println(cInfo("📊 From: ") + walletAddress);
      System.out.println(cInfo("📊 To: ") + input.recipient());
      System.out.println(
          cInfo("📊 Amount: ")
              + input.amount().setScale(18, java.math.RoundingMode.DOWN).toPlainString()
              + " "
              + (input.tokenAddress() == null ? chainProfile.nativeSymbol() : "TOKEN"));
      System.out.println(cInfo("📊 Estimated Gas: ") + estimatedGas);
      System.out.println(cInfo("📊 Gas Price: ") + gasPriceRbtc.toPlainString() + " RBTC");
      System.out.println(cInfo("📊 Total Transaction Cost: ") + totalCost.toPlainString() + " RBTC");
      System.out.println(cInfo("📊 Gas Cost: ") + gasCost.toPlainString() + " RBTC");
      System.out.println(
          cInfo("📊 Value: ")
              + value.setScale(18, java.math.RoundingMode.DOWN).toPlainString()
              + " "
              + (input.tokenAddress() == null ? "RBTC" : "TOKEN"));
    }

    private Integer selectTransactionType() {
      renderedLines = 0;
      int selectedIndex = 0;
      try {
        Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
        NonBlockingReader reader = MENU_TERMINAL.reader();
        try {
          while (true) {
            renderMenu(selectedIndex);
            int key = reader.read();
            if (key < 0) {
              continue;
            }
            if (key == 3) {
              return null;
            }
            if (key == 13 || key == 10) {
              return selectedIndex;
            }
            if (key == 224 || key == 0) {
              selectedIndex = handleWindowsArrow(reader, selectedIndex);
              continue;
            }
            if (key == 27) {
              Integer updated = handleEscapeSequence(reader, selectedIndex);
              if (updated == null) {
                return null;
              }
              selectedIndex = updated;
              continue;
            }
            if (key == 's' || key == 'S' || key == 'j' || key == 'J' || key == 9) {
              selectedIndex = (selectedIndex + 1) % TX_TYPES.length;
              continue;
            }
            if (key == 'w' || key == 'W' || key == 'k' || key == 'K') {
              selectedIndex = (selectedIndex - 1 + TX_TYPES.length) % TX_TYPES.length;
            }
          }
        } finally {
          MENU_TERMINAL.setAttributes(originalAttributes);
          MENU_TERMINAL.writer().flush();
        }
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to render transaction type menu.", ex);
      }
    }

    private void renderMenu(int selectedIndex) {
      if (renderedLines > 0) {
        System.out.print("\u001b[" + renderedLines + "F");
      }
      int lines = 0;
      System.out.println(cOk("✔ 📝 What type of transaction would you like to create? " + TX_TYPES[selectedIndex]));
      lines++;
      for (int i = 0; i < TX_TYPES.length; i++) {
        String prefix = i == selectedIndex ? "❯ " : "  ";
        if (i == selectedIndex) {
          System.out.println(cEmph(prefix + TX_TYPES[i]));
        } else {
          System.out.println(cPlain(prefix + TX_TYPES[i]));
        }
        lines++;
      }
      renderedLines = lines;
      System.out.flush();
    }

    private int handleWindowsArrow(NonBlockingReader reader, int selectedIndex) throws Exception {
      int scan = reader.read(25);
      if (scan == 72) {
        return (selectedIndex - 1 + TX_TYPES.length) % TX_TYPES.length;
      }
      if (scan == 80) {
        return (selectedIndex + 1) % TX_TYPES.length;
      }
      return selectedIndex;
    }

    private Integer handleEscapeSequence(NonBlockingReader reader, int selectedIndex)
        throws Exception {
      int second = reader.read(25);
      if (second == NonBlockingReader.READ_EXPIRED || second < 0) {
        return null;
      }
      if (second == '[' || second == 'O') {
        int third = reader.read(25);
        if (third == 'A') {
          return (selectedIndex - 1 + TX_TYPES.length) % TX_TYPES.length;
        }
        if (third == 'B') {
          return (selectedIndex + 1) % TX_TYPES.length;
        }
      }
      return null;
    }

    private char[] promptPassword() {
      while (true) {
        try {
          Console console = System.console();
          if (console != null) {
            char[] password = console.readPassword(cOk("✔ Enter your password to decrypt the wallet: "));
            if (password == null) {
              throw new PromptCancelledException();
            }
            if (password.length > 0) {
              return password;
            }
          } else {
            String password =
                READER.readLine(cOk("✔ Enter your password to decrypt the wallet: "), '\0');
            if (password == null) {
              throw new PromptCancelledException();
            }
            if (!password.isBlank()) {
              return password.toCharArray();
            }
          }
          System.out.println(cError("❌ Password is required."));
        } catch (UserInterruptException ex) {
          throw new PromptCancelledException();
        }
      }
    }

    private String promptRequired(String label) {
      while (true) {
        try {
          String value = READER.readLine(cOk("✔ " + label + ": "));
          if (value != null && !value.isBlank()) {
            return value.trim();
          }
          System.out.println(cError("❌ Value is required."));
        } catch (UserInterruptException ex) {
          throw new PromptCancelledException();
        }
      }
    }

    private String promptOptionalText(String label) {
      try {
        String value = READER.readLine(cOk("✔ " + label + ": "));
        return value == null ? "" : value.trim();
      } catch (UserInterruptException ex) {
        throw new PromptCancelledException();
      }
    }

    private BigDecimal promptAmount(String label) {
      while (true) {
        try {
          String value = READER.readLine(cOk("✔ " + label + ": "));
          BigDecimal amount = new BigDecimal(value.trim());
          if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return amount;
          }
        } catch (UserInterruptException ex) {
          throw new PromptCancelledException();
        } catch (Exception ignored) {
        }
        System.out.println(cError("❌ Enter a valid positive amount."));
      }
    }

    private BigInteger promptOptionalInteger(String label) {
      while (true) {
        try {
          String value = READER.readLine(cOk("✔ " + label + ": "));
          if (value == null || value.isBlank()) {
            return null;
          }
          return new BigInteger(value.trim());
        } catch (UserInterruptException ex) {
          throw new PromptCancelledException();
        } catch (Exception ignored) {
          System.out.println(cError("❌ Enter a valid integer."));
        }
      }
    }

    private BigDecimal promptOptionalDecimal(String label) {
      while (true) {
        try {
          String value = READER.readLine(cOk("✔ " + label + ": "));
          if (value == null || value.isBlank()) {
            return null;
          }
          return new BigDecimal(value.trim());
        } catch (UserInterruptException ex) {
          throw new PromptCancelledException();
        } catch (Exception ignored) {
          System.out.println(cError("❌ Enter a valid decimal value."));
        }
      }
    }

    private boolean promptYesNo(String label, boolean defaultValue) {
      while (true) {
        try {
          String raw = READER.readLine(cOk("✔ " + label + " " + (defaultValue ? "(Y/n)" : "(y/N)") + ": "));
          if (raw == null || raw.isBlank()) {
            return defaultValue;
          }
          if ("y".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)) {
            return true;
          }
          if ("n".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw)) {
            return false;
          }
        } catch (UserInterruptException ex) {
          throw new PromptCancelledException();
        }
        System.out.println(cError("❌ Please answer yes or no."));
      }
    }

    private String resolveRecipient(ChainProfile chainProfile, String rawValue) {
      return BALANCE_HELPERS.resolveAddressInput(chainProfile, rawValue);
    }

    private String rootMessage(Throwable ex) {
      Throwable current = ex;
      while (current.getCause() != null) {
        current = current.getCause();
      }
      return current.getMessage() == null ? ex.getMessage() : current.getMessage();
    }

    private void redrawWelcome() {
      System.out.print("\u001b[H\u001b[2J");
      System.out.flush();
      WelcomeScreen.printWelcome();
    }
  }

  private record TransactionInput(
      String recipient,
      String tokenAddress,
      BigDecimal amount,
      BigInteger gasLimit,
      BigDecimal gasPriceRbtc,
      String data) {}

  private static class PromptCancelledException extends RuntimeException {}

  private static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  private static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  private static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }

  private static String cWarn(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).a(text).reset().toString();
  }

  private static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  private static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }
}
