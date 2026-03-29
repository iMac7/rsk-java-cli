package com.rsk.commands.transfer;

import static com.rsk.utils.Terminal.*;
import static com.rsk.utils.Format.formatAmount;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Loader;
import com.rsk.utils.Transaction;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final com.rsk.commands.balance.Helpers BALANCE_HELPERS =
      com.rsk.commands.balance.Helpers.defaultHelpers();
  private static final LineReader READER = LineReaderBuilder.builder().build();

  private Subcommands() {}

  @Command(
      name = "transfer",
      description = "Transfer RBTC or ERC20 tokens to the provided address",
      mixinStandardHelpOptions = true)
  public static class TransferCommand implements Callable<Integer> {
    @Option(names = "--wallet", paramLabel = "<wallet>", description = "Name of the wallet")
    String walletName;

    @Option(names = {"-a", "--address"}, paramLabel = "<address>", description = "Recipient address")
    String address;

    @Option(names = "--rns", paramLabel = "<domain>", description = "Recipient RNS domain (e.g., alice.rsk)")
    String rns;

    @Option(
        names = "--token",
        paramLabel = "<address>",
        description = "ERC20 token contract address (optional, for token transfers)")
    String tokenAddress;

    @Option(names = "--value", paramLabel = "<value>", description = "Amount to transfer")
    BigDecimal amount;

    @Option(names = {"-i", "--interactive"}, description = "Execute interactively and input transactions")
    boolean interactive;

    @Option(names = "--gas-limit", paramLabel = "<limit>", description = "Custom gas limit")
    BigInteger gasLimit;

    @Option(names = "--gas-price", paramLabel = "<price>", description = "Custom gas price in RBTC")
    BigDecimal gasPriceRbtc;

    @Option(names = "--data", defaultValue = "", description = "Custom transaction data (hex)")
    String data;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Transfer on the mainnet")
      boolean mainnet;

      @Option(names = {"-t", "--testnet"}, description = "Transfer on the testnet")
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
      String selectedWallet = HELPERS.resolveWalletName(walletName);
      String walletAddress = HELPERS.walletAddress(selectedWallet);
      String tokenContract = HELPERS.resolveTokenAddress(chainProfile, tokenAddress);
      BigInteger resolvedGasPrice =
          gasPriceRbtc == null
              ? Transaction.defaultGasPriceWei(chainProfile)
              : Transaction.gasPriceRbtcToWei(gasPriceRbtc);

      List<Helpers.TransferRequest> requests =
          interactive ? collectInteractiveRequests(chainProfile) : List.of(singleRequest(chainProfile));
      if (requests.isEmpty()) {
        return 0;
      }

      while (true) {
        try {
          char[] password = readPassword("Enter your password to decrypt the wallet: ");
          printWalletContext(chainProfile, walletAddress);

          for (Helpers.TransferRequest request : requests) {
            Helpers.PendingTransfer pendingTransfer =
                tokenContract == null
                    ? HELPERS.sendNative(
                        chainProfile,
                        selectedWallet,
                        password,
                        request.recipient(),
                        Transaction.toWei(request.amount()),
                        gasLimit == null ? Transaction.defaultGasLimit() : gasLimit,
                        resolvedGasPrice,
                        data)
                    : HELPERS.sendToken(
                        chainProfile,
                        selectedWallet,
                        password,
                        tokenContract,
                        request.recipient(),
                        request.amount(),
                        gasLimit,
                        resolvedGasPrice,
                        data);
            System.out.println(cInfo("🔄 Transaction initiated. TxHash: ") + pendingTransfer.txHash());
            var receipt =
                Loader.runWithSpinner(
                    "Waiting for confirmation...",
                    () ->
                        Transaction.waitForSuccessfulReceipt(
                            chainProfile, pendingTransfer.txHash(), 120, 2000L));
            printTransferResult(receipt);
          }
          return 0;
        } catch (IllegalStateException | IllegalArgumentException ex) {
          if (!interactive) {
            throw ex;
          }
          System.out.println(
              cError(ex.getMessage() == null ? "Transfer failed." : ex.getMessage()));
        }
      }
    }

    private Helpers.TransferRequest singleRequest(ChainProfile chainProfile) {
      if ((address == null || address.isBlank()) && (rns == null || rns.isBlank())) {
        throw new IllegalArgumentException("Provide --address or --rns, and --value, or use --interactive.");
      }
      String rawTarget = resolveSingleTarget();
      if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Provide --value greater than zero, or use --interactive.");
      }
      return new Helpers.TransferRequest(resolveTarget(chainProfile, rawTarget), amount);
    }

    private String resolveSingleTarget() {
      if (rns != null && !rns.isBlank()) {
        return rns;
      }
      if (address != null && !address.isBlank()) {
        return address;
      }
      throw new IllegalArgumentException("Provide --address or --rns, and --value, or use --interactive.");
    }

    private List<Helpers.TransferRequest> collectInteractiveRequests(ChainProfile chainProfile) {
      List<Helpers.TransferRequest> requests = new ArrayList<>();
      while (true) {
        String rawTarget = promptRequiredText("Enter address");
        String resolvedTarget = resolveTarget(chainProfile, rawTarget);
        BigDecimal value = promptAmount("Enter amount");
        requests.add(new Helpers.TransferRequest(resolvedTarget, value));
        if (!promptYesNo("Add another transaction?", false)) {
          return requests;
        }
      }
    }

    private String resolveTarget(ChainProfile chainProfile, String rawTarget) {
      return BALANCE_HELPERS.resolveAddressInput(chainProfile, rawTarget);
    }

    private void printWalletContext(ChainProfile chainProfile, String walletAddress) {
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
    return com.rsk.utils.Terminal.readPassword(cOk("✔" + prompt), "Transfer cancelled.");
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
        throw new IllegalStateException("Transfer cancelled.");
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
        throw new IllegalStateException("Transfer cancelled.");
      }
    }
  }
}
