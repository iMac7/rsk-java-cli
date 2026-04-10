package com.rsk.commands.simulate;

import com.rsk.java_cli.CliHelpers;
import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.Callable;
import org.web3j.protocol.Web3j;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public class Subcommands {
  private Subcommands() {}

  @Command(name = "simulate", description = "Simulation builder", mixinStandardHelpOptions = true)
  public static class SimulateCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    @Option(names = {"-a", "--address"}, required = true, description = "Recipient address or RNS")
    String address;

    @Option(names = "--value", required = true, description = "Transfer amount (RBTC or token units)")
    BigDecimal value;

    @Option(names = "--token", description = "ERC20 token contract address")
    String token;

    @Option(names = {"-w", "--wallet"}, description = "Wallet name (defaults to active wallet)")
    String wallet;

    @Option(names = "--gas-limit", description = "Custom gas limit override")
    BigInteger gasLimit;

    @Option(names = "--gas-price", description = "Custom gas price in RBTC")
    BigDecimal gasPriceRbtc;

    @Option(names = "--data", defaultValue = "", description = "Custom tx data (hex)")
    String data;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use rootstock mainnet")
      boolean mainnet;
      @Option(names = "--testnet", description = "Use rootstock testnet")
      boolean testnet;
      @Option(names = "--chain", paramLabel = "<name>", description = "Use config chain key, e.g. chains.custom.<name> or <name>")
      String chain;
      @Option(names = "--chainurl", paramLabel = "<url>", description = "Use an explicit RPC URL")
      String chainUrl;
    }

    @Override
    public Integer call() {
      if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("--value must be greater than zero.");
      }
      ChainProfile chainProfile =
          helpers().resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);
      WalletMetadata walletMeta = helpers().resolveWallet(wallet);
      String toAddress = helpers().resolveAddressInput(chainProfile, address);

      try {
        Web3j web3j = Rpc.web3j(chainProfile);
        if (token == null || token.isBlank()) {
          helpers().simulateRbtc(
              chainProfile, web3j, walletMeta, toAddress, value, gasLimit, gasPriceRbtc, data);
        } else {
          helpers().simulateErc20(
              chainProfile, web3j, walletMeta, toAddress, token, value, gasLimit, gasPriceRbtc, data);
        }
      } catch (Exception ex) {
        throw new IllegalStateException("Simulation failed.", ex);
      }
      return 0;
    }

    private Helpers helpers() {
      return CliHelpers.deps(spec).simulateHelpers();
    }
  }
}
