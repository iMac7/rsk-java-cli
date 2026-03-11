package com.rsk.commands.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.rsk.utils.Terminal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final LineReader PROMPT_READER = createPromptReader();

  private Subcommands() {}

  private static LineReader createPromptReader() {
    if (Terminal.interactiveTerminal() != null) {
      return LineReaderBuilder.builder().terminal(Terminal.interactiveTerminal()).build();
    }
    return LineReaderBuilder.builder().build();
  }

  @Command(name = "contract", description = "Interact with a contract", mixinStandardHelpOptions = true)
  public static class ContractCommand implements Callable<Integer> {
    @Option(
        names = {"-a", "--address"},
        required = true,
        paramLabel = "<address>",
        description = "Address of a verified contract")
    String address;

    @Option(names = {"-t", "--testnet"}, description = "Deploy on the testnet")
    boolean testnet;

    @Override
    public Integer call() {
      HELPERS.validateAddress(address);
      var chainProfile = HELPERS.resolveChain(false, testnet, null, null);

      System.out.println(cEmph("🔧 Initializing interaction on " + networkLabel(chainProfile) + "..."));
      System.out.println(cInfo("🔎 Checking if contract " + address + " is verified..."));
      System.out.println();

      JsonNode abiArray = HELPERS.resolveAbiArrayFromBlockscout(chainProfile, address);
      List<JsonNode> readFunctions = HELPERS.readFunctions(abiArray);
      if (readFunctions.isEmpty()) {
        throw new IllegalStateException("No read functions found in verified ABI.");
      }

      String[] menuItems = buildMenuItems(readFunctions);
      int exitIndex = menuItems.length - 1;

      while (true) {
        int selectedIndex = selectMenu("Select a read function to call:", menuItems);
        if (selectedIndex == exitIndex) {
          System.out.println();
          System.out.println(cMuted("Exiting contract mode."));
          return 0;
        }

        JsonNode selectedFunction = readFunctions.get(selectedIndex);
        String functionName = selectedFunction.path("name").asText();
        try {
          List<Type> inputs = promptFunctionInputs(selectedFunction);
          List<TypeReference<?>> outputRefs = outputRefs(selectedFunction);
          List<Type> results =
              HELPERS.executeReadFunction(chainProfile, address, functionName, inputs, outputRefs);

          System.out.println();
          System.out.println(cMuted("────────────────────────────────────────"));
          System.out.println(cOk("✓ Function call succeeded"));
          System.out.println(cMuted("Contract: " + address));
          System.out.println(cMuted("Function: " + functionSignature(selectedFunction)));
          printResults(results);
          System.out.println(cMuted("Explorer: " + HELPERS.blockscoutAddressUrl(chainProfile, address)));
          System.out.println(cMuted("────────────────────────────────────────"));
          System.out.println();
        } catch (IllegalArgumentException | IllegalStateException ex) {
          System.out.println();
          System.out.println(cMuted("────────────────────────────────────────"));
          System.out.println(cError("Read failed: " + messageOrFallback(ex, "Contract read failed.")));
          System.out.println(cMuted("Contract: " + address));
          System.out.println(cMuted("Function: " + functionSignature(selectedFunction)));
          System.out.println(cMuted("────────────────────────────────────────"));
          System.out.println();
        }
      }
    }

    private List<Type> promptFunctionInputs(JsonNode functionNode) {
      List<Type> inputs = new ArrayList<>();
      JsonNode inputNodes = functionNode.path("inputs");
      if (!inputNodes.isArray() || inputNodes.isEmpty()) {
        return inputs;
      }

      System.out.println();
      System.out.println(cInfo("Enter function arguments:"));
      for (int i = 0; i < inputNodes.size(); i++) {
        JsonNode input = inputNodes.get(i);
        String type = input.path("type").asText();
        String argName = input.path("name").asText();
        String label = (argName == null || argName.isBlank()) ? "arg" + (i + 1) : argName;
        String raw = readRequiredText(label + " (" + type + ")");
        inputs.add(HELPERS.toAbiInputType(type, raw));
      }
      return inputs;
    }

    private List<TypeReference<?>> outputRefs(JsonNode functionNode) {
      List<TypeReference<?>> outputRefs = new ArrayList<>();
      JsonNode outputNodes = functionNode.path("outputs");
      if (!outputNodes.isArray()) {
        return outputRefs;
      }
      for (JsonNode output : outputNodes) {
        outputRefs.add(HELPERS.outputTypeReference(output.path("type").asText()));
      }
      return outputRefs;
    }

    private void printResults(List<Type> results) {
      if (results.isEmpty()) {
        System.out.println(cPlain("Result: (no return value)"));
        return;
      }
      if (results.size() == 1) {
        System.out.println(cPlain("Result: " + HELPERS.readableTypeValue(results.get(0))));
        return;
      }
      for (int i = 0; i < results.size(); i++) {
        System.out.println(cPlain("Result[" + i + "]: " + HELPERS.readableTypeValue(results.get(i))));
      }
    }

    private int selectMenu(String title, String[] menuItems) {
      int selectedIndex =
          Terminal.selectMenu(
              List.of("? " + title),
              menuItems,
              List.of("↑↓ navigate • ⏎ select • Esc/Ctrl+C cancel"),
              -1,
              "contract");
      if (selectedIndex < 0) {
        throw new InteractiveCancelledException();
      }
      return selectedIndex;
    }

    private String functionMenuLabel(JsonNode functionNode) {
      return functionNode.path("name").asText();
    }

    private String[] buildMenuItems(List<JsonNode> readFunctions) {
      String[] menuItems = new String[readFunctions.size() + 1];
      for (int i = 0; i < readFunctions.size(); i++) {
        menuItems[i] = functionMenuLabel(readFunctions.get(i));
      }
      menuItems[readFunctions.size()] = Terminal.pick("🚪 Exit", "[exit] Exit");
      return menuItems;
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

    private String networkLabel(com.rsk.utils.Chain.ChainProfile chainProfile) {
      if (chainProfile.chainId() == 31L) {
        return "testnet";
      }
      if (chainProfile.chainId() == 30L) {
        return "mainnet";
      }
      return chainProfile.name();
    }
  }

  static class InteractiveCancelledException extends RuntimeException {
    InteractiveCancelledException() {
      super("Interactive contract action cancelled.");
    }
  }

  static String readRequiredText(String label) {
    while (true) {
      try {
        String value = PROMPT_READER.readLine(buildPrompt(label));
        if (value == null) {
          throw new InteractiveCancelledException();
        }
        if (!value.isBlank()) {
          return value.trim();
        }
        System.out.println(cError("Value is required."));
      } catch (UserInterruptException ex) {
        throw new InteractiveCancelledException();
      }
    }
  }

  private static String buildPrompt(String label) {
    return Ansi.ansi()
        .fg(Ansi.Color.GREEN)
        .a("✔ ")
        .reset()
        .fg(Ansi.Color.WHITE)
        .a(label)
        .a(": ")
        .reset()
        .toString();
  }

  private static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  private static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  private static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  private static String cMuted(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }

  private static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  private static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }

  private static String messageOrFallback(Exception ex, String fallback) {
    return ex.getMessage() == null || ex.getMessage().isBlank() ? fallback : ex.getMessage();
  }
}
