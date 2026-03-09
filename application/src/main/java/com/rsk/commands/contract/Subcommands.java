package com.rsk.commands.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.rsk.utils.Chain.ChainProfile;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "contract", description = "Interactive contract mode", mixinStandardHelpOptions = true)
  public static class ContractCommand implements Callable<Integer> {
    @Option(names = "--address", required = true, paramLabel = "<address>", description = "Verified contract address")
    String address;

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
      HELPERS.validateAddress(address);
      ChainProfile chainProfile =
          HELPERS.resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      System.out.printf("Initializing interaction on %s...%n", chainProfile.name());
      System.out.println("Checking if contract " + address + " is verified...");

      JsonNode abiArray = HELPERS.resolveAbiArrayFromBlockscout(chainProfile, address);
      List<JsonNode> readFunctions = HELPERS.readFunctions(abiArray);
      if (readFunctions.isEmpty()) {
        throw new IllegalStateException("No read functions found in verified ABI.");
      }

      System.out.println("Available read functions:");
      for (int i = 0; i < readFunctions.size(); i++) {
        JsonNode fn = readFunctions.get(i);
        String name = fn.path("name").asText();
        int inputCount = fn.path("inputs").isArray() ? fn.path("inputs").size() : 0;
        System.out.printf("%d) %s (%d args)%n", i + 1, name, inputCount);
      }

      int selectedIndex;
      while (true) {
        String raw = readRequiredTextPrompt("Select function number", "1");
        try {
          selectedIndex = Integer.parseInt(raw) - 1;
          if (selectedIndex >= 0 && selectedIndex < readFunctions.size()) {
            break;
          }
        } catch (NumberFormatException ignored) {
        }
        System.out.println("Please select a valid function number.");
      }

      JsonNode selectedFunction = readFunctions.get(selectedIndex);
      String functionName = selectedFunction.path("name").asText();
      System.out.println("You selected: " + functionName);

      List<Type> inputs = new ArrayList<>();
      JsonNode inputNodes = selectedFunction.path("inputs");
      if (inputNodes.isArray()) {
        for (int i = 0; i < inputNodes.size(); i++) {
          JsonNode input = inputNodes.get(i);
          String type = input.path("type").asText();
          String argName = input.path("name").asText();
          String label = (argName == null || argName.isBlank()) ? ("arg" + i) : argName;
          String raw = readRequiredTextPrompt("Enter " + label + " (" + type + ")", "");
          inputs.add(HELPERS.toAbiInputType(type, raw));
        }
      }

      List<TypeReference<?>> outputRefs = new ArrayList<>();
      JsonNode outputNodes = selectedFunction.path("outputs");
      if (outputNodes.isArray()) {
        for (JsonNode output : outputNodes) {
          outputRefs.add(HELPERS.outputTypeReference(output.path("type").asText()));
        }
      }

      List<Type> results =
          HELPERS.executeReadFunction(chainProfile, address, functionName, inputs, outputRefs);

      System.out.println();
      System.out.println("Function " + functionName + " called successfully.");
      if (results.isEmpty()) {
        System.out.println("Result: (no return value)");
      } else if (results.size() == 1) {
        System.out.println("Result: " + HELPERS.readableTypeValue(results.get(0)));
      } else {
        for (int i = 0; i < results.size(); i++) {
          System.out.println("Result[" + i + "]: " + HELPERS.readableTypeValue(results.get(i)));
        }
      }
      System.out.println("Explorer: " + HELPERS.blockscoutAddressUrl(chainProfile, address));
      return 0;
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
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          System.out.println("Input cancelled.");
          continue;
        }
        throw ex;
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
