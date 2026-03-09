package com.rsk.commands.tx;

import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Monitorsession;
import com.rsk.utils.Rpc;
import com.rsk.utils.Storage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = defaultHelpers();

  private Subcommands() {}

  private static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".evm-cli");
    return new Helpers(
        new Rpc.Web3jRpcGateway(), new Storage.JsonMonitorSessionRepository(homeDir));
  }

  @Command(
      name = "tx",
      description = "Transaction tools",
      mixinStandardHelpOptions = true,
      subcommands = {Status.class, Monitor.class})
  public static class TxCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      throw new picocli.CommandLine.ParameterException(
          new picocli.CommandLine(this), "Specify a tx subcommand.");
    }
  }

  @Command(name = "status", description = "Show transaction receipt status", mixinStandardHelpOptions = true)
  static class Status implements Callable<Integer> {
    @Option(names = "--tx", required = true, paramLabel = "<hash>", description = "Transaction hash")
    String txHash;

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

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(mainnet, testnet, chain, chainUrl);
      Optional<String> status = HELPERS.receiptStatus(chainProfile, txHash);
      System.out.println(status.orElse("PENDING"));
      return 0;
    }
  }

  @Command(name = "monitor", description = "Start tx confirmation monitor", mixinStandardHelpOptions = true)
  static class Monitor implements Callable<Integer> {
    @Option(names = "--tx", required = true, paramLabel = "<hash>", description = "Transaction hash")
    String txHash;

    @Option(names = "--interval", defaultValue = "10", description = "Polling interval in seconds")
    int pollIntervalSeconds;

    @Option(names = "--confirmations", defaultValue = "1", description = "Required confirmations")
    int confirmationsRequired;

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

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(mainnet, testnet, chain, chainUrl);
      Monitorsession.MonitorSession session =
          HELPERS.startTxConfirmations(
              chainProfile.name(), txHash, pollIntervalSeconds, confirmationsRequired);
      System.out.printf("Started monitor session %s for %s%n", session.getId(), txHash);
      return 0;
    }
  }

  private static ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    Path homeDir = Path.of(System.getProperty("user.home"), ".evm-cli");
    com.rsk.commands.config.CliConfig config =
        new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)).loadConfig();
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile("custom-url", chainUrl, 0L, "NATIVE", "", "", ChainFeatures.defaults());
    }
    String chainOption = normalizeChainOption(chain);
    boolean useMainnet = mainnet;
    boolean useTestnet = testnet;
    if ("mainnet".equals(chainOption)) {
      useMainnet = true;
      useTestnet = false;
      chainOption = null;
    } else if ("testnet".equals(chainOption)) {
      useMainnet = false;
      useTestnet = true;
      chainOption = null;
    }
    return Chain.resolve(config, new Chain.ChainSelection(useMainnet, useTestnet || !useMainnet, chainOption));
  }

  private static String normalizeChainOption(String chainOption) {
    if (chainOption == null || chainOption.isBlank()) {
      return chainOption;
    }
    String normalized = chainOption.trim();
    if (normalized.startsWith("chains.custom.")) {
      return normalized.substring("chains.custom.".length());
    }
    if ("chains.mainnet".equals(normalized)) {
      return "mainnet";
    }
    if ("chains.testnet".equals(normalized)) {
      return "testnet";
    }
    return normalized;
  }
}
