package com.rsk.commands.tx;

import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Monitorsession;
import com.rsk.utils.Rpc;
import com.rsk.utils.Storage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = defaultHelpers();
  private static final com.rsk.commands.monitor.Helpers MONITOR_HELPERS =
      com.rsk.commands.monitor.Helpers.defaultHelpers();

  private Subcommands() {}

  private static Helpers defaultHelpers() {
    return new Helpers(new Rpc.Web3jRpcGateway());
  }

  @Command(
      name = "tx",
      description = "Transaction status",
      mixinStandardHelpOptions = true)
  public static class TxCommand implements Callable<Integer> {
    @Option(names = "--txid", required = true, paramLabel = "<hash>", description = "Transaction hash")
    String txid;

    @Option(names = "--monitor", description = "Start a confirmation monitor session")
    boolean monitor;

    @Option(names = "--confirmations", defaultValue = "1", description = "Required confirmations")
    int confirmations;

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
      if (monitor) {
        Monitorsession.MonitorSession session =
            MONITOR_HELPERS.startTxConfirmations(chainProfile.name(), txid, 10, confirmations);
        System.out.println("Monitor session started: " + session.getId());
        return 0;
      }
      Optional<String> status = HELPERS.receiptStatus(chainProfile, txid);
      System.out.println(status.map(s -> "Receipt status: " + s).orElse("Receipt not found yet."));
      return 0;
    }
  }

  private static ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
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
    return Chain.resolve(config, new Chain.ChainSelection(useMainnet, useTestnet, chainOption));
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
