package com.rsk.commands.batchtransfer;

import com.rsk.commands.transfer.Helpers.TransferRequest;
import com.rsk.utils.CliInput;
import com.rsk.utils.Loader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final LineReader READER = LineReaderBuilder.builder().build();

  private Subcommands() {}

  @Command(
      name = "batch-transfer",
      description = "Execute batch transactions interactively or from a JSON file",
      mixinStandardHelpOptions = true)
  public static class BatchTransferCommand implements Callable<Integer> {
    @Option(names = {"-t", "--testnet"}, description = "Execute on the testnet")
    boolean testnet;

    @Option(
        names = "--rns",
        description = "Enable RNS domain resolution for recipient addresses")
    boolean rnsEnabled;

    @ArgGroup(exclusive = true, multiplicity = "1")
    InputOptions inputOptions;

    static class InputOptions {
      @Option(names = {"-i", "--interactive"}, description = "Execute interactively and input transactions")
      boolean interactive;

      @Option(
          names = {"-f", "--file"},
          paramLabel = "<path>",
          description = {
            "Execute transactions from a JSON file.",
            "Expected structure:",
            "[",
            "  {",
            "    \"to\": \"0x742d35Cc6634C0532925a3b844Bc454e4438f44e\",",
            "    \"value\": 0.1",
            "  },",
            "  {",
            "    \"to\": \"alice.rsk\",",
            "    \"value\": 1.5",
            "  }",
            "]",
            "Each item must include a recipient `to` and a positive numeric `value`."
          })
      Path file;
    }

    @Override
    public Integer call() {
      try {
        var chainProfile = HELPERS.resolveChain(testnet);
        String selectedWallet = HELPERS.resolveWalletName();
        String walletAddress = HELPERS.walletAddress(selectedWallet);
        Helpers.TransferDefaults defaults = HELPERS.transferDefaults(chainProfile);

        List<TransferRequest> requests =
            inputOptions.interactive
                ? collectInteractiveRequests(chainProfile)
                : loadRequestsFromFile(chainProfile, inputOptions.file);
        if (requests.isEmpty()) {
          System.out.println(cError("No transactions provided."));
          return 0;
        }

        String privateKeyHex = promptForUnlockedKey(selectedWallet);
        printWalletContext(chainProfile, walletAddress);

        for (TransferRequest request : requests) {
          try {
            var pendingTransfer =
                HELPERS.submitTransfer(chainProfile, privateKeyHex, request, defaults);
            System.out.println(cInfo("🔄 Transaction initiated. TxHash: ") + pendingTransfer.txHash());
            org.web3j.protocol.core.methods.response.TransactionReceipt receipt =
                Loader.runWithSpinner(
                    "Waiting for confirmation...",
                    () -> HELPERS.waitForConfirmation(chainProfile, pendingTransfer));
            printTransferResult(receipt);
          } catch (Exception ex) {
            System.out.println(cError(HELPERS.rootMessage(ex)));
            return 0;
          }
        }
        return 0;
      } catch (IllegalArgumentException | IllegalStateException ex) {
        System.out.println(cError(HELPERS.rootMessage(ex)));
        return 0;
      }
    }

    private String promptForUnlockedKey(String walletName) {
      while (true) {
        char[] password = readPassword("Enter your password to decrypt the wallet: ");
        try {
          return HELPERS.unlockWalletPrivateKeyHex(walletName, password);
        } catch (IllegalArgumentException ex) {
          System.out.println(cError(HELPERS.rootMessage(ex)));
        }
      }
    }

    private List<TransferRequest> collectInteractiveRequests(
        com.rsk.utils.Chain.ChainProfile chainProfile) {
      List<TransferRequest> requests = new ArrayList<>();
      while (true) {
        String target = promptRequiredText("Enter address");
        String resolvedTarget = HELPERS.resolveRecipient(chainProfile, target);
        BigDecimal amount = promptAmount("Enter amount");
        requests.add(new TransferRequest(resolvedTarget, amount));
        if (!promptYesNo("Add another transaction?", false)) {
          return requests;
        }
      }
    }

    private List<TransferRequest> loadRequestsFromFile(
        com.rsk.utils.Chain.ChainProfile chainProfile, Path filePath) {
      return HELPERS.loadRequestsFromFile(chainProfile, filePath);
    }

    private void printWalletContext(com.rsk.utils.Chain.ChainProfile chainProfile, String walletAddress) {
      System.out.println(cInfo("📄 Wallet Address: ") + walletAddress);
      System.out.println(
          cInfo("💰 Current Balance: ")
              + formatAmount(HELPERS.nativeBalance(chainProfile, walletAddress))
              + " "
              + chainProfile.nativeSymbol());
    }

    private void printTransferResult(org.web3j.protocol.core.methods.response.TransactionReceipt receipt) {
      System.out.println(cOk("✅ Transaction confirmed successfully!"));
      System.out.println(cInfo("📦 Block Number: ") + receipt.getBlockNumber());
      System.out.println(cInfo("⛽ Gas Used: ") + receipt.getGasUsed());
    }
  }

  static char[] readPassword(String prompt) {
    return CliInput.readPassword(cOk("✔" + prompt), "Batch transfer cancelled.");
  }

  private static String promptRequiredText(String label) {
    while (true) {
      try {
        String value = READER.readLine(cOk("✔ " + label + ": "));
        if (value != null && !value.isBlank()) {
          return value.trim();
        }
        System.out.println(cError("Value is required."));
      } catch (UserInterruptException ex) {
        throw new IllegalStateException("Batch transfer cancelled.");
      }
    }
  }

  private static BigDecimal promptAmount(String label) {
    while (true) {
      String raw = promptRequiredText(label);
      try {
        BigDecimal amount = new BigDecimal(raw);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
          throw new IllegalArgumentException();
        }
        return amount;
      } catch (Exception ex) {
        System.out.println(cError("Enter a valid positive amount."));
      }
    }
  }

  private static boolean promptYesNo(String label, boolean defaultValue) {
    while (true) {
      try {
        String suffix = defaultValue ? "Y/n" : "y/N";
        String raw = READER.readLine(label + " (" + suffix + "): ");
        if (raw == null || raw.isBlank()) {
          return defaultValue;
        }
        if ("y".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)) {
          return true;
        }
        if ("n".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw)) {
          return false;
        }
        System.out.println(cError("Please answer y or n."));
      } catch (UserInterruptException ex) {
        throw new IllegalStateException("Batch transfer cancelled.");
      }
    }
  }

  private static String formatAmount(BigDecimal amount) {
    BigDecimal stripped = amount.stripTrailingZeros();
    if (stripped.scale() < 0) {
      stripped = stripped.setScale(0);
    }
    return stripped.toPlainString();
  }

  private static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  private static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  private static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }
}
