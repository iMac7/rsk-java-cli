package com.rsk.commands.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.rsk.utils.TerminalText;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  private static final Terminal MENU_TERMINAL = createTerminal();
  private static final LineReader PROMPT_READER = createPromptReader();

  private Subcommands() {}

  private static Terminal createTerminal() {
    try {
      return TerminalBuilder.builder().system(true).encoding(StandardCharsets.UTF_8).build();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static LineReader createPromptReader() {
    if (MENU_TERMINAL != null) {
      return LineReaderBuilder.builder().terminal(MENU_TERMINAL).build();
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

    private int renderedLines;

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
      renderedLines = 0;
      int selectedIndex = 0;

      if (MENU_TERMINAL == null) {
        throw new IllegalStateException("Unable to initialize contract terminal.");
      }

      try {
        Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
        NonBlockingReader reader = MENU_TERMINAL.reader();

        try {
          while (true) {
            renderMenu(title, menuItems, selectedIndex);
            int key = reader.read();
            if (key < 0) {
              continue;
            }
            if (key == 3) {
              renderedLines = 0;
              throw new InteractiveCancelledException();
            }
            if (key == 13 || key == 10) {
              renderedLines = 0;
              return selectedIndex;
            }
            if (isForwardCycleKey(key)) {
              selectedIndex = moveDown(selectedIndex, menuItems.length);
              continue;
            }
            if (isBackwardCycleKey(key)) {
              selectedIndex = moveUp(selectedIndex, menuItems.length);
              continue;
            }
            if (key == 224 || key == 0) {
              selectedIndex = handleWindowsArrow(reader, selectedIndex, menuItems.length);
              continue;
            }
            if (key == 27) {
              Integer updated = handleEscapeSequence(reader, selectedIndex, menuItems.length);
              if (updated == null) {
                renderedLines = 0;
                throw new InteractiveCancelledException();
              }
              selectedIndex = updated;
            }
          }
        } finally {
          MENU_TERMINAL.setAttributes(originalAttributes);
          MENU_TERMINAL.writer().flush();
        }
      } catch (EndOfFileException ex) {
        throw new InteractiveCancelledException();
      } catch (InteractiveCancelledException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to render contract function menu.", ex);
      }
    }

    private void renderMenu(String title, String[] menuItems, int selectedIndex) {
      if (renderedLines > 0) {
        System.out.print("\u001b[" + renderedLines + "F");
      }

      int lines = 0;

      System.out.println(cInfo("? " + title));
      lines++;

      for (int i = 0; i < menuItems.length; i++) {
        String pointer =
            i == selectedIndex ? TerminalText.pick("❯ ", "> ") : "  ";
        if (i == selectedIndex) {
          System.out.println(cEmph(pointer + menuItems[i]));
        } else {
          System.out.println(cPlain(pointer + menuItems[i]));
        }
        lines++;
      }

      System.out.println(cMuted("↑↓ navigate • ⏎ select • Esc/Ctrl+C cancel"));
      lines++;

      renderedLines = lines;
      System.out.flush();
    }

    private Integer handleEscapeSequence(NonBlockingReader reader, int selectedIndex, int itemCount) {
      try {
        int second = reader.read(25);
        if (second == NonBlockingReader.READ_EXPIRED || second < 0) {
          return null;
        }
        if (second == '[' || second == 'O') {
          int third = reader.read(25);
          if (third == 'A') {
            return moveUp(selectedIndex, itemCount);
          }
          if (third == 'B') {
            return moveDown(selectedIndex, itemCount);
          }
        }
        return null;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to read keyboard escape sequence.", ex);
      }
    }

    private int handleWindowsArrow(NonBlockingReader reader, int selectedIndex, int itemCount) {
      try {
        int scan = reader.read(25);
        if (scan == 72) {
          return moveUp(selectedIndex, itemCount);
        }
        if (scan == 80) {
          return moveDown(selectedIndex, itemCount);
        }
        return selectedIndex;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to read keyboard scan code.", ex);
      }
    }

    private int moveUp(int selectedIndex, int itemCount) {
      return (selectedIndex - 1 + itemCount) % itemCount;
    }

    private int moveDown(int selectedIndex, int itemCount) {
      return (selectedIndex + 1) % itemCount;
    }

    private boolean isForwardCycleKey(int key) {
      return key == 9 || key == 's' || key == 'S' || key == 'j' || key == 'J';
    }

    private boolean isBackwardCycleKey(int key) {
      return key == 'w' || key == 'W' || key == 'k' || key == 'K';
    }

    private String functionMenuLabel(JsonNode functionNode) {
      return functionNode.path("name").asText();
    }

    private String[] buildMenuItems(List<JsonNode> readFunctions) {
      String[] menuItems = new String[readFunctions.size() + 1];
      for (int i = 0; i < readFunctions.size(); i++) {
        menuItems[i] = functionMenuLabel(readFunctions.get(i));
      }
      menuItems[readFunctions.size()] = TerminalText.pick("🚪 Exit", "[exit] Exit");
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
