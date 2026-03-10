package com.rsk.commands.monitor;

import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Monitorsession;
import com.rsk.utils.Storage;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "monitor", description = "Session monitoring", mixinStandardHelpOptions = true)
  public static class MonitorCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", paramLabel = "<session-id>", description = "Session id")
    UUID sessionId;

    @Option(names = "--list", description = "List monitor sessions")
    boolean list;

    @Option(names = "--stop", paramLabel = "<session-id>", description = "Stop session")
    UUID stop;

    @Option(names = "--tx", paramLabel = "<hash>", description = "Start tx confirmation monitor")
    String txHash;

    @Option(names = "--interval", defaultValue = "10", description = "Polling interval in seconds")
    int pollIntervalSeconds;

    @Option(names = "--confirmations", defaultValue = "1", description = "Required confirmations")
    int confirmationsRequired;

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
      if (sessionId != null) {
        Monitorsession.MonitorSession session =
            HELPERS.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        printSession(session);
        return 0;
      }

      if (list) {
        HELPERS.listSessions().stream()
            .sorted(Comparator.comparing(Monitorsession.MonitorSession::getCreatedAt))
            .forEach(Subcommands::printSessionSummary);
        return 0;
      }

      if (stop != null) {
        HELPERS.stopSession(stop);
        System.out.println("Stopped session " + stop);
        return 0;
      }

      if (txHash != null && !txHash.isBlank()) {
        ChainProfile chainProfile =
            resolveChain(
                networkOptions.mainnet,
                networkOptions.testnet,
                networkOptions.chain,
                networkOptions.chainUrl);
        Monitorsession.MonitorSession session =
            HELPERS.startTxConfirmations(
                chainProfile.name(), txHash, pollIntervalSeconds, confirmationsRequired);
        System.out.println("Started tx monitor session " + session.getId());
        return 0;
      }

      System.out.println("Use --list, --stop, --tx, or provide <session-id>.");
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

  private static void printSessionSummary(Monitorsession.MonitorSession session) {
    System.out.printf(
        "%s %s %s %s%n",
        session.getId(), session.getType(), session.getStatus(), session.getTarget());
  }

  private static void printSession(Monitorsession.MonitorSession session) {
    System.out.printf("Session: %s%n", session.getId());
    System.out.printf("Type: %s%n", session.getType());
    System.out.printf("Status: %s%n", session.getStatus());
    System.out.printf("Chain: %s%n", session.getChainRef());
    System.out.printf("Target: %s%n", session.getTarget());
    System.out.printf("Created: %s%n", session.getCreatedAt());
    System.out.printf("Poll interval: %ss%n", session.getPollIntervalSeconds());
    System.out.printf("Confirmations: %s%n", session.getConfirmationsRequired());
    System.out.printf("Checks: %s%n", session.getCheckCount());
    System.out.printf("Last checked: %s%n", session.getLastCheckedAt());
  }
}
