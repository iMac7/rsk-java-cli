package com.rsk.commands.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.rsk.utils.Chain.ChainProfile;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "history", description = "History API", mixinStandardHelpOptions = true)
  public static class HistoryCommand implements Callable<Integer> {
    @Option(names = "--apikey", description = "Alchemy API key")
    String apiKey;

    @Option(names = "--from-block")
    String fromBlock;

    @Option(names = "--to-block")
    String toBlock;

    @Option(names = "--from-address")
    String fromAddress;

    @Option(names = "--to-address")
    String toAddress;

    @Option(names = "--exclude-zero-value")
    String excludeZeroValue;

    @Option(
        names = "--category",
        description =
            "Requires non-empty comma-separated categories. Allowed: external, internal, erc20, erc721, erc1155, specialnft")
    String category;

    @Option(names = "--maxcount", description = "Hex maxCount (or decimal, auto-converted)")
    String maxCount;

    @Option(names = "--number", description = "Alias for maxCount")
    String number;

    @Option(names = "--order", description = "asc or desc")
    String order;

    @Option(names = "--contract-addresses", description = "Comma-separated contract addresses")
    String contractAddresses;

    @Option(names = "--pagekey")
    String pageKey;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use chains.mainnet")
      boolean mainnet;
      @Option(names = "--testnet", description = "Use chains.testnet")
      boolean testnet;
      @Option(names = "--chain", paramLabel = "<name>", description = "Use config chain key, e.g. chains.custom.<name> or <name>")
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
      String resolvedApiKey = HELPERS.resolveApiKey(apiKey);
      String maxCountHex = HELPERS.normalizeHexCount(maxCount != null ? maxCount : number);
      JsonNode body =
          HELPERS.alchemyAssetTransfersRequest(
              fromBlock,
              toBlock,
              fromAddress,
              toAddress,
              excludeZeroValue,
              category,
              maxCountHex,
              order,
              contractAddresses,
              pageKey);
      String url = HELPERS.resolveAlchemyUrl(chainProfile, resolvedApiKey);
      JsonNode response = HELPERS.postJson(url, body);
      System.out.println(HELPERS.prettyPrint(response));
      return 0;
    }
  }
}
