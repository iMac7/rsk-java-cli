package com.rsk.commands.bridge;

import static com.rsk.utils.Terminal.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Terminal;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.datatypes.Type;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "bridge", description = "Interact with RSK Bridge contract", mixinStandardHelpOptions = true)
  public static class BridgeCommand implements Callable<Integer> {
    @Option(names = "--wallet", description = "Wallet name for write calls (defaults to active wallet)")
    String wallet;

    @Option(names = "--value", description = "RBTC value for payable write calls")
    BigDecimal value;

    @Option(names = "--gas-limit", description = "Gas limit override for write calls")
    BigInteger gasLimit;

    @Option(names = "--gas-price", description = "Gas price in wei override for write calls")
    BigInteger gasPrice;

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
          HELPERS.resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      System.out.println();
      System.out.println(cEmph("Initializing bridge for " + networkLabel(chainProfile) + "..."));

      String contractAddress = HELPERS.bridgeAddress();
      JsonNode abiArray = HELPERS.readAbiArrayResource();
      List<JsonNode> functions = HELPERS.functions(abiArray);
      if (functions.isEmpty()) {
        throw new IllegalStateException("No bridge functions available in ABI.");
      }

      List<JsonNode> readFunctions = new ArrayList<>();
      List<JsonNode> writeFunctions = new ArrayList<>();
      for (JsonNode function : functions) {
        if (isReadOnly(function)) {
          readFunctions.add(function);
        } else {
          writeFunctions.add(function);
        }
      }

      System.out.println(cMuted("Bridge contract: " + contractAddress));

      int selectedTypeIndex =
          Terminal.selectMenu(
              List.of("? Select the type of function you want to call:"),
              new String[] {"read", "write"},
              List.of("Up/Down navigate | Enter select | Esc/Ctrl+C cancel"),
              -1,
              "bridge");
      if (selectedTypeIndex < 0) {
        System.out.println(cMuted("Bridge interaction cancelled."));
        return 0;
      }

      boolean readOnly = selectedTypeIndex == 0;
      List<JsonNode> selectedFunctions = readOnly ? readFunctions : writeFunctions;
      if (selectedFunctions.isEmpty()) {
        throw new IllegalStateException(
            "No " + (readOnly ? "read" : "write") + " bridge functions available in ABI.");
      }

      int selectedIndex =
          Terminal.selectMenu(
              List.of("? Select a " + (readOnly ? "read" : "write") + " bridge function to call:"),
              selectedFunctions.stream().map(this::functionMenuLabel).toArray(String[]::new),
              List.of("Up/Down navigate | Enter select | Esc/Ctrl+C cancel"),
              -1,
              "bridge");
      if (selectedIndex < 0) {
        System.out.println(cMuted("Bridge interaction cancelled."));
        return 0;
      }

      JsonNode selected = selectedFunctions.get(selectedIndex);

      try {
        if (readOnly) {
          List<Type> results =
              HELPERS.executeRead(
                  chainProfile,
                  contractAddress,
                  selected,
                  (label, type) -> readRequiredTextPrompt("Enter " + label + " (" + type + ")", ""));
          printReadResult(chainProfile, contractAddress, selected, results);
        } else {
          String walletName = HELPERS.resolveWalletName(wallet);
          char[] password = readPassword("Wallet password: ");
          Helpers.WriteResult result =
              HELPERS.executeWrite(
                  chainProfile,
                  contractAddress,
                  selected,
                  walletName,
                  password,
                  value,
                  gasLimit,
                  gasPrice,
                  (label, type) -> readRequiredTextPrompt("Enter " + label + " (" + type + ")", ""));
          printWriteResult(chainProfile, contractAddress, selected, result);
        }
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Bridge interaction failed.", ex);
      }
    }

    private boolean isReadOnly(JsonNode functionNode) {
      String mutability = functionNode.path("stateMutability").asText();
      return "view".equals(mutability) || "pure".equals(mutability);
    }

    private String functionMenuLabel(JsonNode fn) {
      String name = fn.path("name").asText();
      String mutability = fn.path("stateMutability").asText();
      int inputCount = fn.path("inputs").isArray() ? fn.path("inputs").size() : 0;
      return name + " [" + mutability + "] (" + inputCount + " args)";
    }

    private void printReadResult(
        ChainProfile chainProfile, String contractAddress, JsonNode functionNode, List<Type> results) {
      System.out.println();
      System.out.println(cRule());
      System.out.println(cEmph("Bridge Read Result"));
      System.out.println(cInfo("Network: ") + networkLabel(chainProfile));
      System.out.println(cInfo("Contract: ") + contractAddress);
      System.out.println(cInfo("Function: ") + functionSignature(functionNode));
      System.out.println(cInfo("Mode: ") + cOk("read"));
      System.out.println();
      System.out.println(cEmph("Return Data"));
      if (results.isEmpty()) {
        System.out.println(cPlain("  (no return value)"));
      } else if (results.size() == 1) {
        System.out.println(cPlain("  " + HELPERS.readableTypeValue(results.get(0))));
      } else {
        for (int i = 0; i < results.size(); i++) {
          System.out.println(cPlain("  [" + i + "] " + HELPERS.readableTypeValue(results.get(i))));
        }
      }
      System.out.println();
      System.out.println(cMuted("Explorer: " + HELPERS.explorerAddressUrl(chainProfile, contractAddress)));
      System.out.println(cRule());
      System.out.println();
    }

    private void printWriteResult(
        ChainProfile chainProfile,
        String contractAddress,
        JsonNode functionNode,
        Helpers.WriteResult result) {
      System.out.println();
      System.out.println(cRule());
      System.out.println(cEmph("Bridge Write Result"));
      System.out.println(cInfo("Network: ") + networkLabel(chainProfile));
      System.out.println(cInfo("Contract: ") + contractAddress);
      System.out.println(cInfo("Function: ") + functionSignature(functionNode));
      System.out.println(cInfo("Mode: ") + cWarn("write"));
      System.out.println(cInfo("Wallet: ") + result.walletAddress());
      System.out.println();
      System.out.println(cEmph("Transaction Receipt"));
      System.out.println(cPlain("  Hash: " + result.txHash()));
      System.out.println(cPlain("  Block: " + result.blockNumber()));
      System.out.println(cPlain("  Gas Used: " + result.gasUsed()));
      System.out.println();
      System.out.println(cMuted("Tx Explorer: " + HELPERS.explorerTxUrl(chainProfile, result.txHash())));
      System.out.println(cMuted("Contract Explorer: " + HELPERS.explorerAddressUrl(chainProfile, contractAddress)));
      System.out.println(cRule());
      System.out.println();
    }

    private String functionSignature(JsonNode functionNode) {
      StringBuilder builder = new StringBuilder(functionNode.path("name").asText()).append("(");
      JsonNode inputs = functionNode.path("inputs");
      if (inputs.isArray()) {
        for (int i = 0; i < inputs.size(); i++) {
          if (i > 0) {
            builder.append(", ");
          }
          builder.append(inputs.get(i).path("type").asText());
        }
      }
      return builder.append(")").toString();
    }

    private String networkLabel(ChainProfile chainProfile) {
      if (chainProfile.chainId() == 31L) {
        return "testnet";
      }
      if (chainProfile.chainId() == 30L) {
        return "mainnet";
      }
      return chainProfile.name();
    }
  }

  static char[] readPassword(String prompt) {
    return com.rsk.utils.Terminal.readPassword(prompt, "Bridge interaction cancelled.");
  }

  static String readTextPrompt(String label, String defaultValue) {
    String prompt =
        (defaultValue == null || defaultValue.isBlank())
            ? label + ": "
            : label + " [" + defaultValue + "]: ";
    while (true) {
      try {
        Console console = System.console();
        if (console != null) {
          String value = console.readLine("%s", prompt);
          if ((value == null || value.isBlank()) && defaultValue != null) {
            return defaultValue;
          }
          return value == null ? "" : value.trim();
        }
        System.out.print(prompt);
        System.out.flush();
        String value = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if ((value == null || value.isBlank()) && defaultValue != null) {
          return defaultValue;
        }
        return value == null ? "" : value.trim();
      } catch (IOException ex) {
        throw new IllegalStateException("Unable to read interactive input", ex);
      }
    }
  }

  static String readRequiredTextPrompt(String label, String defaultValue) {
    while (true) {
      String value = readTextPrompt(label, defaultValue);
      if (!value.isBlank()) {
        return value;
      }
      System.out.println("Value is required.");
    }
  }
}
