package com.rsk.commands.resolve;

import com.rsk.java_cli.CliHelpers;
import com.rsk.utils.Chain.ChainProfile;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

public class Subcommands {
  private Subcommands() {}

  @Command(name = "resolve", description = "Resolve RNS names to and from addresses", mixinStandardHelpOptions = true)
  public static class ResolveCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<value>", description = "RNS name or address")
    String value;

    @Option(names = "--reverse", description = "Resolve address to RNS name")
    boolean reverse;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use rootstock mainnet")
      boolean mainnet;

      @Option(names = "--testnet", description = "Use rootstock testnet")
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
          helpers().resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);
      String resolved =
          reverse
              ? helpers().reverseResolve(chainProfile, value)
              : helpers().resolveName(chainProfile, value);
      System.out.println(resolved);
      return 0;
    }

    private Helpers helpers() {
      return CliHelpers.deps(spec).resolveHelpers();
    }
  }
}
