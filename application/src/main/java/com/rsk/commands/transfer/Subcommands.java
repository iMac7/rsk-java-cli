package com.rsk.commands.transfer;

import com.rsk.utils.Chain.ChainProfile;
import java.io.Console;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.Callable;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final com.rsk.commands.transfer.Helpers HELPERS =
      com.rsk.commands.transfer.Helpers.defaultHelpers();
  private static final com.rsk.commands.balance.Helpers BALANCE_HELPERS =
      com.rsk.commands.balance.Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "transfer", description = "Send native transfer", mixinStandardHelpOptions = true)
  public static class TransferCommand implements Callable<Integer> {
    @Option(names = "--wallet", paramLabel = "<name>", description = "Wallet name")
    String walletName;

    @Option(names = "--to", required = true, paramLabel = "<target>", description = "Address or RNS name")
    String target;

    @Option(names = "--amount", required = true, paramLabel = "<value>", description = "Amount in native units")
    BigDecimal amount;

    @Option(names = "--gas-limit", paramLabel = "<wei>", description = "Gas limit override")
    BigInteger gasLimit;

    @Option(names = "--gas-price", paramLabel = "<wei>", description = "Gas price override")
    BigInteger gasPriceWei;

    @Option(names = "--data", defaultValue = "", description = "Optional hex data")
    String data;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

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
      if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("--amount must be greater than zero.");
      }

      String selectedWallet =
          walletName != null && !walletName.isBlank()
              ? walletName
              : com.rsk.commands.wallet.Helpers.defaultHelpers()
                  .activeWallet()
                  .map(com.rsk.commands.wallet.Helpers.WalletMetadata::name)
                  .orElseThrow(
                      () -> new IllegalArgumentException("No active wallet found. Provide --wallet."));

      ChainProfile chainProfile =
          HELPERS.resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      String resolvedTo = BALANCE_HELPERS.resolveAddressInput(chainProfile, target);
      BigInteger valueWei = HELPERS.toWei(amount);
      BigInteger resolvedGasLimit = gasLimit == null ? HELPERS.defaultGasLimit() : gasLimit;
      BigInteger resolvedGasPrice =
          gasPriceWei == null ? HELPERS.defaultGasPriceWei(chainProfile) : gasPriceWei;
      char[] password = readPassword("Wallet password: ");
      String txHash =
          HELPERS.sendNative(
              chainProfile, selectedWallet, password, resolvedTo, valueWei, resolvedGasLimit, resolvedGasPrice, data);

      System.out.printf("Transfer submitted on %s: %s%n", chainProfile.name(), txHash);
      return 0;
    }
  }

  static char[] readPassword(String prompt) {
    while (true) {
      try {
        Console console = System.console();
        if (console != null) {
          char[] password = console.readPassword(prompt);
          if (password == null || password.length == 0) {
            System.out.println("Password is required.");
            continue;
          }
          return password;
        }
        String password = LineReaderBuilder.builder().build().readLine(prompt, '*');
        if (password == null || password.isBlank()) {
          System.out.println("Password is required.");
          continue;
        }
        return password.toCharArray();
      } catch (UserInterruptException ex) {
        throw new IllegalStateException("Transfer cancelled.");
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          throw new IllegalStateException("Transfer cancelled.");
        }
        throw ex;
      }
    }
  }
}
