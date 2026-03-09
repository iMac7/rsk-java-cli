package com.rsk.commands.balance;

import com.rsk.utils.Chain.ChainProfile;
import java.math.BigInteger;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "balance", description = "Get native balance", mixinStandardHelpOptions = true)
  public static class BalanceCommand implements Callable<Integer> {
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    Target target;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

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

      BigInteger wei;
      try {
        wei = HELPERS.nativeBalanceWei(chainProfile, address);
      } catch (Exception ex) {
        throw new IllegalStateException(
            "Unable to fetch balance on network '"
                + chainProfile.name()
                + "'. Try --testnet, --mainnet, --chain chains.custom.<name>, or --chainurl <rpcUrl>.",
            ex);
      }

      String amountDisplay =
          Ansi.ansi()
              .fg(Ansi.Color.GREEN)
              .a(
                  String.format(
                      "%s wei (%s %s)",
                      wei, HELPERS.toNative(wei).toPlainString(), chainProfile.nativeSymbol()))
              .reset()
              .toString();
      System.out.printf("%s balance on %s: %s%n", address, chainProfile.name(), amountDisplay);
      return 0;
    }
  }
}
