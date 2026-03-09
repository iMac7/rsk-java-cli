package com.rsk.commands.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.rsk.utils.Chain.ChainProfile;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.datatypes.Type;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "bridge", description = "Bridge flow", mixinStandardHelpOptions = true)
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
      System.out.printf("Initializing bridge interaction on %s...%n", chainProfile.name());
      String contractAddress = HELPERS.bridgeAddress();
      JsonNode abiArray = HELPERS.readAbiArrayResource();
      List<JsonNode> functions = HELPERS.functions(abiArray);
      if (functions.isEmpty()) {
        throw new IllegalStateException("No bridge functions available in ABI.");
      }

      System.out.println("Bridge contract: " + contractAddress);
      System.out.println("Available functions:");
      for (int i = 0; i < functions.size(); i++) {
        JsonNode fn = functions.get(i);
        String name = fn.path("name").asText();
        String mutability = fn.path("stateMutability").asText();
        int inputCount = fn.path("inputs").isArray() ? fn.path("inputs").size() : 0;
        System.out.printf("%d) %s [%s] (%d args)%n", i + 1, name, mutability, inputCount);
      }

      int selectedIndex;
      while (true) {
        String raw = readRequiredTextPrompt("Select function number", "1");
        try {
          selectedIndex = Integer.parseInt(raw) - 1;
          if (selectedIndex >= 0 && selectedIndex < functions.size()) {
            break;
          }
        } catch (NumberFormatException ignored) {
        }
        System.out.println("Please select a valid function number.");
      }

      JsonNode selected = functions.get(selectedIndex);
      String functionName = selected.path("name").asText();
      String mutability = selected.path("stateMutability").asText();
      System.out.println("You selected: " + functionName + " [" + mutability + "]");

      try {
        boolean readOnly = "view".equals(mutability) || "pure".equals(mutability);
        if (readOnly) {
          List<Type> results =
              HELPERS.executeRead(
                  chainProfile,
                  contractAddress,
                  selected,
                  (label, type) -> readRequiredTextPrompt("Enter " + label + " (" + type + ")", ""));
          System.out.println("Function " + functionName + " called successfully!");
          if (results.isEmpty()) {
            System.out.println("Result: (no return value)");
          } else if (results.size() == 1) {
            System.out.println("Result: " + HELPERS.readableTypeValue(results.get(0)));
          } else {
            for (int i = 0; i < results.size(); i++) {
              System.out.println("Result[" + i + "]: " + HELPERS.readableTypeValue(results.get(i)));
            }
          }
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
          System.out.println("Wallet account: " + result.walletAddress());
          System.out.println("Transaction confirmed successfully!");
          System.out.println("Transaction Hash: " + result.txHash());
          System.out.println("Block Number: " + result.blockNumber());
          System.out.println("Gas Used: " + result.gasUsed());
          System.out.println("Explorer: " + HELPERS.explorerTxUrl(chainProfile, result.txHash()));
        }
        System.out.println("Explorer: " + HELPERS.explorerAddressUrl(chainProfile, contractAddress));
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Bridge interaction failed.", ex);
      }
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
        System.out.print(prompt);
        System.out.flush();
        String password = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (password == null || password.isBlank()) {
          System.out.println("Password is required.");
          continue;
        }
        return password.toCharArray();
      } catch (IOException ex) {
        throw new IllegalStateException("Unable to read interactive input", ex);
      }
    }
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
