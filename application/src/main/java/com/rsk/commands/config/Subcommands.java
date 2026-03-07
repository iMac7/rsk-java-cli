package com.rsk.commands.config;

import com.evmcli.domain.model.ChainFeatures;
import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.model.CliConfig;
import java.util.Map;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.Command;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "config", description = "Config UI", mixinStandardHelpOptions = true)
  public static class ConfigCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      CliConfig config = HELPERS.loadConfig();
      LineReader reader = LineReaderBuilder.builder().build();
      boolean dirty = false;

      try {
        while (true) {
          printConfigSummary(config, dirty);
          String choice;
          try {
            choice = promptText(reader, "Select option", "");
          } catch (EndOfFileException e) {
            System.out.println();
            return 0;
          }

          switch (choice) {
            case "1" -> {
              config
                  .getChains()
                  .setMainnet(promptChainProfile(reader, "mainnet", config.getChains().getMainnet()));
              dirty = true;
            }
            case "2" -> {
              config
                  .getChains()
                  .setTestnet(promptChainProfile(reader, "testnet", config.getChains().getTestnet()));
              dirty = true;
            }
            case "3" -> {
              String key = promptRequiredText(reader, "Custom chain key", "");
              ChainProfile existing = config.getChains().getCustom().get(key);
              ChainProfile updated = promptChainProfile(reader, key, existing);
              config.getChains().getCustom().put(key, updated);
              dirty = true;
            }
            case "4" -> {
              if (config.getChains().getCustom().isEmpty()) {
                System.out.println("No custom chains to remove.");
              } else {
                String key = promptRequiredText(reader, "Custom chain key to remove", "");
                ChainProfile removed = config.getChains().getCustom().remove(key);
                if (removed == null) {
                  System.out.println("Custom chain not found: " + key);
                } else {
                  System.out.println("Removed custom chain: " + key);
                  dirty = true;
                }
              }
            }
            case "5" -> {
              boolean current = config.getWallet().isCachePasswordInMemory();
              config.getWallet().setCachePasswordInMemory(!current);
              System.out.println(
                  "Wallet password cache is now " + (!current ? "enabled" : "disabled") + ".");
              dirty = true;
            }
            case "6" -> {
              String currentKey = config.getApiKeys().getAlchemyApiKey();
              String keyInput =
                  promptText(
                      reader,
                      "Alchemy API key (leave blank to clear)",
                      currentKey == null ? "" : currentKey);
              config.getApiKeys().setAlchemyApiKey(keyInput == null ? "" : keyInput.trim());
              dirty = true;
            }
            case "7" -> {
              HELPERS.saveConfig(config);
              System.out.println("Config saved.");
              return 0;
            }
            case "8" -> {
              if (dirty && !promptBoolean(reader, "Discard unsaved changes and exit", false)) {
                break;
              }
              return 0;
            }
            default -> System.out.println("Unknown option.");
          }
        }
      } catch (InteractiveCancelledException ignored) {
        System.out.println();
        return 0;
      }
    }
  }

  static String promptText(LineReader reader, String label, String defaultValue) {
    String prompt;
    if (defaultValue == null || defaultValue.isBlank()) {
      prompt = Ansi.ansi().fg(Ansi.Color.WHITE).a(label + ": ").reset().toString();
    } else {
      prompt =
          Ansi.ansi()
              .fg(Ansi.Color.GREEN)
              .a(label + " [")
              .a(defaultValue)
              .a("]: ")
              .reset()
              .toString();
    }

    while (true) {
      try {
        String value = reader.readLine(prompt);
        if ((value == null || value.isBlank()) && defaultValue != null) {
          return defaultValue;
        }
        return value == null ? "" : value.trim();
      } catch (UserInterruptException ignored) {
        throw new InteractiveCancelledException();
      }
    }
  }

  static class InteractiveCancelledException extends RuntimeException {
    InteractiveCancelledException() {
      super("Interactive config action cancelled.");
    }
  }

  static String promptRequiredText(LineReader reader, String label, String defaultValue) {
    while (true) {
      String value = promptText(reader, label, defaultValue);
      if (!value.isBlank()) {
        return value;
      }
      System.out.println("Value is required.");
    }
  }

  static long promptLong(LineReader reader, String label, long defaultValue) {
    while (true) {
      String raw = promptText(reader, label, Long.toString(defaultValue));
      try {
        return Long.parseLong(raw);
      } catch (NumberFormatException ex) {
        System.out.println("Please enter a valid integer.");
      }
    }
  }

  static boolean promptBoolean(LineReader reader, String label, boolean defaultValue) {
    while (true) {
      String raw = promptText(reader, label + " (y/n)", defaultValue ? "y" : "n");
      if (raw.equalsIgnoreCase("y") || raw.equalsIgnoreCase("yes")) {
        return true;
      }
      if (raw.equalsIgnoreCase("n") || raw.equalsIgnoreCase("no")) {
        return false;
      }
      System.out.println("Please answer with y or n.");
    }
  }

  static ChainProfile promptChainProfile(LineReader reader, String slotName, ChainProfile existing) {
    String currentName = existing == null || existing.name() == null ? slotName : existing.name();
    String currentRpcUrl = existing == null || existing.rpcUrl() == null ? "" : existing.rpcUrl();
    long currentChainId = existing == null ? 0L : existing.chainId();
    String currentNativeSymbol =
        existing == null || existing.nativeSymbol() == null ? "" : existing.nativeSymbol();
    String currentTxTemplate =
        existing == null || existing.explorerTxUrlTemplate() == null
            ? ""
            : existing.explorerTxUrlTemplate();
    String currentAddressTemplate =
        existing == null || existing.explorerAddressUrlTemplate() == null
            ? ""
            : existing.explorerAddressUrlTemplate();
    ChainFeatures currentFeatures =
        existing == null || existing.features() == null ? ChainFeatures.defaults() : existing.features();

    System.out.println("Editing chain profile: " + slotName);
    String name = promptRequiredText(reader, "Name", currentName);
    String rpcUrl = promptRequiredText(reader, "RPC URL", currentRpcUrl);
    long chainId = promptLong(reader, "Chain ID", currentChainId);
    String nativeSymbol = promptRequiredText(reader, "Native symbol", currentNativeSymbol);
    String txTemplate = promptRequiredText(reader, "Explorer tx URL template", currentTxTemplate);
    String addressTemplate =
        promptRequiredText(reader, "Explorer address URL template", currentAddressTemplate);
    boolean supportsNameResolution =
        promptBoolean(reader, "Supports name resolution", currentFeatures.supportsNameResolution());
    boolean supportsBridge = promptBoolean(reader, "Supports bridge", currentFeatures.supportsBridge());
    boolean supportsContractVerification =
        promptBoolean(
            reader,
            "Supports contract verification",
            currentFeatures.supportsContractVerification());
    boolean supportsHistoryApi =
        promptBoolean(reader, "Supports history API", currentFeatures.supportsHistoryApi());

    return new ChainProfile(
        name,
        rpcUrl,
        chainId,
        nativeSymbol,
        txTemplate,
        addressTemplate,
        new ChainFeatures(
            supportsNameResolution,
            supportsBridge,
            supportsContractVerification,
            supportsHistoryApi));
  }

  static void printConfigSummary(CliConfig config, boolean dirty) {
    System.out.println();
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).bold().a("=== Config UI ===").reset());
    if (dirty) {
      System.out.println(Ansi.ansi().fgRgb(255, 183, 77).a("* Unsaved changes").reset());
    }
    System.out.printf(
        "%s%s%n",
        Ansi.ansi().fg(Ansi.Color.GREEN).a("Wallet cache password in memory: ").reset(),
        config.getWallet().isCachePasswordInMemory() ? "enabled" : "disabled");
    String alchemyKey = config.getApiKeys().getAlchemyApiKey();
    System.out.printf(
        "%s%s%n",
        Ansi.ansi().fg(Ansi.Color.GREEN).a("Alchemy API key: ").reset(),
        (alchemyKey == null || alchemyKey.isBlank()) ? "(not set)" : "(set)");
    printChainSlot("mainnet", config.getChains().getMainnet());
    printChainSlot("testnet", config.getChains().getTestnet());
    System.out.println(Ansi.ansi().fg(Ansi.Color.GREEN).a("Custom chains:").reset());
    if (config.getChains().getCustom().isEmpty()) {
      System.out.println("- (none)");
    } else {
      for (Map.Entry<String, ChainProfile> entry : config.getChains().getCustom().entrySet()) {
        ChainProfile profile = entry.getValue();
        System.out.printf(
            "- %s -> rpc=%s chainId=%d symbol=%s%n",
            Ansi.ansi().fg(Ansi.Color.GREEN).a(entry.getKey()).reset(),
            profile.rpcUrl(),
            profile.chainId(),
            profile.nativeSymbol());
      }
    }
    System.out.println();
    printNumberedOption(1, "Edit mainnet");
    printNumberedOption(2, "Edit testnet");
    printNumberedOption(3, "Add or update custom chain");
    printNumberedOption(4, "Remove custom chain");
    printNumberedOption(5, "Toggle wallet password cache");
    printNumberedOption(6, "Set Alchemy API key");
    printNumberedOption(7, "Save and exit");
    printNumberedOption(8, "Exit without saving");
    System.out.println();
  }

  static void printChainSlot(String label, ChainProfile profile) {
    if (profile == null) {
      System.out.printf(
          "%s%s%n", Ansi.ansi().fg(Ansi.Color.GREEN).a(label + ": ").reset(), "(not set)");
      return;
    }
    System.out.printf(
        "%sname=%s rpc=%s chainId=%d symbol=%s%n",
        Ansi.ansi().fg(Ansi.Color.GREEN).a(label + ": ").reset(),
        profile.name(),
        profile.rpcUrl(),
        profile.chainId(),
        profile.nativeSymbol());
  }

  static void printNumberedOption(int number, String text) {
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fgRgb(255, 153, 51).bold().a(number + ")").reset(),
        Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset());
  }
}
