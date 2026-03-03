package com.evmcli.cli;

import com.evmcli.application.ChainSelection;
import com.evmcli.application.ChainSelector;
import com.evmcli.application.utils.rns.lookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evmcli.domain.model.ChainFeatures;
import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.model.CliConfig;
import com.evmcli.domain.model.MonitorSession;
import com.evmcli.domain.model.WalletMetadata;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "evm-cli",
    description = "EVM multi-chain CLI",
    subcommands = {
      EvmCliCommand.VersionCommand.class,
      EvmCliCommand.WalletCommand.class,
      EvmCliCommand.ConfigCommand.class,
      EvmCliCommand.BalanceCommand.class,
      EvmCliCommand.TransferCommand.class,
      EvmCliCommand.TxCommand.class,
      EvmCliCommand.MonitorCommand.class,
      EvmCliCommand.ResolveCommand.class,
      EvmCliCommand.DeployCommand.class,
      EvmCliCommand.VerifyCommand.class,
      EvmCliCommand.ContractCommand.class,
      EvmCliCommand.BridgeCommand.class,
      EvmCliCommand.HistoryCommand.class,
      EvmCliCommand.BatchTransferCommand.class,
      EvmCliCommand.TransactionCommand.class,
      EvmCliCommand.SimulateCommand.class
    })
public class EvmCliCommand implements Callable<Integer> {
  private static final String INPUT_CANCELLED_MESSAGE = "Input cancelled. Please try again.";
  private static final String ALCHEMY_ROOTSTOCK_MAINNET_URL =
      "https://rootstock-mainnet.g.alchemy.com/v2/%s";
  private static final String ALCHEMY_ROOTSTOCK_TESTNET_URL =
      "https://rootstock-testnet.g.alchemy.com/v2/%s";
  private static final String BLOCKSCOUT_VERIFY_MAINNET_URL =
      "https://rootstock.blockscout.com/api/v2/smart-contracts/%s/verification/via/standard-input";
  private static final String BLOCKSCOUT_VERIFY_TESTNET_URL =
      "https://rootstock-testnet.blockscout.com/api/v2/smart-contracts/%s/verification/via/standard-input";
  private static final String BLOCKSCOUT_CONTRACT_MAINNET_URL =
      "https://rootstock.blockscout.com/api/v2/smart-contracts/%s";
  private static final String BLOCKSCOUT_CONTRACT_TESTNET_URL =
      "https://rootstock-testnet.blockscout.com/api/v2/smart-contracts/%s";
  private static final String BLOCKSCOUT_ADDRESS_API_MAINNET_URL =
      "https://rootstock.blockscout.com/api/v2/addresses/%s";
  private static final String BLOCKSCOUT_ADDRESS_API_TESTNET_URL =
      "https://rootstock-testnet.blockscout.com/api/v2/addresses/%s";
  private static final String BLOCKSCOUT_ADDRESS_MAINNET_URL =
      "https://rootstock.blockscout.com/address/%s";
  private static final String BLOCKSCOUT_ADDRESS_TESTNET_URL =
      "https://rootstock-testnet.blockscout.com/address/%s";
  private static final String RSK_BRIDGE_CONTRACT = "0x0000000000000000000000000000000001000006";
  private static final String BRIDGE_ABI_RESOURCE = "bridge_abi.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static CliContext ctx;

  @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
  boolean helpRequested;

  @Option(names = "--json", description = "Structured JSON output (stub)")
  boolean json;

  public EvmCliCommand(CliContext context) {
    ctx = context;
  }

  @Override
  public Integer call() {
    System.out.println("Use --help to see available commands.");
    return 0;
  }

  static String cliVersion() {
    String implementationVersion = EvmCliCommand.class.getPackage().getImplementationVersion();
    if (implementationVersion != null && !implementationVersion.isBlank()) {
      return implementationVersion;
    }
    return "0.1.0";
  }

  static ChainProfile resolveChain(NetworkOptions options) {
    String chainUrl = options.selector.chainUrl;
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile(
          "custom-url",
          chainUrl,
          0L,
          "NATIVE",
          "",
          "",
          ChainFeatures.defaults());
    }

    String chainOption = normalizeChainOption(options.selector.chain);
    if ("mainnet".equals(chainOption)) {
      options.selector.mainnet = true;
      options.selector.testnet = false;
      chainOption = null;
    } else if ("testnet".equals(chainOption)) {
      options.selector.mainnet = false;
      options.selector.testnet = true;
      chainOption = null;
    }

    return ChainSelector.resolve(
        ctx.configPort().load(),
        new ChainSelection(
            options.selector.mainnet,
            options.selector.testnet || (!options.selector.mainnet && chainOption == null),
            chainOption));
  }

  static String normalizeChainOption(String chainOption) {
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
        String password = LineReaderBuilder.builder().build().readLine(prompt, '*');
        if (password == null || password.isBlank()) {
          System.out.println("Password is required.");
          continue;
        }
        return password.toCharArray();
      } catch (org.jline.reader.UserInterruptException ex) {
        System.out.println(INPUT_CANCELLED_MESSAGE);
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          System.out.println(INPUT_CANCELLED_MESSAGE);
          continue;
        }
        throw ex;
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
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          System.out.println(INPUT_CANCELLED_MESSAGE);
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

  static void ensureConfigShape(CliConfig config) {
    if (config.getChains() == null) {
      config.setChains(new CliConfig.Chains());
    }
    if (config.getChains().getCustom() == null) {
      config.getChains().setCustom(new LinkedHashMap<>());
    }
    if (config.getWallet() == null) {
      config.setWallet(new CliConfig.WalletPreferences());
    }
    if (config.getApiKeys() == null) {
      config.setApiKeys(new CliConfig.ApiKeys());
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
        System.out.println(INPUT_CANCELLED_MESSAGE);
      }
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
        promptBoolean(
            reader, "Supports name resolution", currentFeatures.supportsNameResolution());
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

  static boolean isHexAddress(String value) {
    return value != null && value.matches("(?i)^0x[a-f0-9]{40}$");
  }

  static boolean isValidRnsName(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase();
    if (normalized.length() < 3 || normalized.length() > 255 || !normalized.contains(".")) {
      return false;
    }
    if (normalized.startsWith(".") || normalized.endsWith(".")) {
      return false;
    }
    String[] labels = normalized.split("\\.");
    for (String label : labels) {
      if (label.isEmpty() || label.length() > 63) {
        return false;
      }
      if (!label.matches("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$")) {
        return false;
      }
    }
    return true;
  }

  static String resolveAddressInput(ChainProfile chainProfile, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Address value is required.");
    }
    String trimmed = value.trim();
    if (isHexAddress(trimmed)) {
      return trimmed;
    }
    if (!isValidRnsName(trimmed)) {
      throw new IllegalArgumentException(
          "Invalid RNS format: "
              + trimmed
              + ". Expected domain-like format, e.g. alice.rsk");
    }
    return lookup
        .lookup(chainProfile.rpcUrl(), trimmed)
        .orElseThrow(() -> new IllegalArgumentException("RNS name not found: " + trimmed));
  }

  static Optional<ChainProfile> resolveChainProfileByRef(String chainRef) {
    CliConfig config = ctx.configPort().load();
    if (chainRef == null || chainRef.isBlank()) {
      return Optional.empty();
    }
    if ("mainnet".equalsIgnoreCase(chainRef)) {
      return Optional.ofNullable(config.getChains().getMainnet());
    }
    if ("testnet".equalsIgnoreCase(chainRef)) {
      return Optional.ofNullable(config.getChains().getTestnet());
    }

    ChainProfile byCustomKey = config.getChains().getCustom().get(chainRef);
    if (byCustomKey != null) {
      return Optional.of(byCustomKey);
    }

    return config.getChains().getCustom().values().stream()
        .filter(profile -> profile.name() != null && profile.name().equalsIgnoreCase(chainRef))
        .findFirst();
  }

  static String formatInstant(Instant timestamp) {
    if (timestamp == null) {
      return "n/a";
    }
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    return formatter.format(timestamp);
  }

  static String formatConfirmations(MonitorSession session) {
    int required = Math.max(1, session.getConfirmationsRequired());
    long current = Math.min(session.getCheckCount(), required);
    return current + "/" + required;
  }

  static boolean confirmationsReached(MonitorSession session) {
    return session.getConfirmationsRequired() > 0
        && session.getCheckCount() >= session.getConfirmationsRequired();
  }

  static void printMonitorSessionHeader(MonitorSession session, ChainProfile chainProfile) {
    String chainName = chainProfile == null ? session.getChainRef() : chainProfile.name();
    String chainId = chainProfile == null ? "n/a" : Long.toString(chainProfile.chainId());
    String rpcUrl = chainProfile == null ? "n/a" : chainProfile.rpcUrl();
    String symbol = chainProfile == null ? "n/a" : chainProfile.nativeSymbol();
    String senderWallet = "unknown";

    System.out.println(
        Ansi.ansi()
            .fgRgb(255, 153, 51)
            .bold()
            .a("🛰️  Monitor Session Viewer")
            .reset());
    System.out.println();

    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("🆔 Session:").reset(),
        session.getId());
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("📌 Status:").reset(),
        Ansi.ansi().fg(Ansi.Color.YELLOW).a("Monitoring").reset());
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("🧭 Type:").reset(),
        session.getType());
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("🔗 Network:").reset(),
        chainName);
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("🆔 Chain ID:").reset(),
        chainId);
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("🌐 RPC:").reset(),
        rpcUrl);
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("💱 Native:").reset(),
        symbol);
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("👛 Sending wallet:").reset(),
        senderWallet);
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("🧾 Tx Hash:").reset(),
        session.getTarget());
    System.out.printf(
        "%s %s%n",
        Ansi.ansi().fg(Ansi.Color.CYAN).a("📅 Created:").reset(),
        formatInstant(session.getCreatedAt()));
    System.out.println();
  }

  static void printMonitorSessionDynamic(MonitorSession session, String spinnerFrame, boolean updateInPlace) {
    boolean complete = confirmationsReached(session);
    Ansi.Color statusColor = complete ? Ansi.Color.GREEN : Ansi.Color.YELLOW;
    String statusLine =
        (complete ? "✅ Confirmations reached" : spinnerFrame + " Waiting for confirmations")
            + " | status="
            + session.getStatus();
    String progressLine =
        "✅ Confirmations: "
            + formatConfirmations(session)
            + " | 🔁 Checks: "
            + session.getCheckCount()
            + " | ⏱️ Poll: "
            + session.getPollIntervalSeconds()
            + "s";
    String timeLine =
        "🕒 Last check: "
            + formatInstant(session.getLastCheckedAt())
            + " | 🕰️ Now: "
            + formatInstant(Instant.now());

    if (updateInPlace) {
      System.out.print("\u001b[3A");
    }
    System.out.print("\u001b[2K\r" + Ansi.ansi().fg(statusColor).bold().a(statusLine).reset() + "\n");
    System.out.print("\u001b[2K\r" + Ansi.ansi().fg(Ansi.Color.CYAN).a(progressLine).reset() + "\n");
    System.out.print("\u001b[2K\r" + Ansi.ansi().fg(Ansi.Color.CYAN).a(timeLine).reset() + "\n");
    System.out.flush();
  }

  static void monitorSessionTui(UUID sessionId) {
    String[] spinnerFrames = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    int spinnerIndex = 0;
    boolean dynamicPrinted = false;
    while (true) {
      MonitorSession session =
          ctx.monitorManager()
              .find(sessionId)
              .orElseThrow(() -> new IllegalArgumentException("Monitor session not found: " + sessionId));
      ChainProfile chainProfile = resolveChainProfileByRef(session.getChainRef()).orElse(null);
      if (!dynamicPrinted) {
        printMonitorSessionHeader(session, chainProfile);
      }
      printMonitorSessionDynamic(
          session, spinnerFrames[spinnerIndex % spinnerFrames.length], dynamicPrinted);
      dynamicPrinted = true;
      spinnerIndex++;

      if (confirmationsReached(session) || session.getStatus() != MonitorSession.Status.ACTIVE) {
        System.out.println();
        if (confirmationsReached(session)) {
          System.out.println(
              Ansi.ansi()
                  .fg(Ansi.Color.GREEN)
                  .bold()
                  .a("🎉 Monitoring complete. Confirmations requirement satisfied.")
                  .reset());
        }
        return;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  static String readRequiredFile(String path, String label) {
    try {
      String content = Files.readString(Path.of(path));
      if (content == null || content.isBlank()) {
        throw new IllegalArgumentException(label + " file is empty: " + path);
      }
      return content;
    } catch (IOException ex) {
      throw new IllegalArgumentException("Unable to read " + label + " file: " + path, ex);
    }
  }

  static List<String> constructorTypesFromAbi(String abiJson) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(abiJson);
      if (!root.isArray()) {
        throw new IllegalArgumentException("ABI must be a JSON array.");
      }
      for (JsonNode item : root) {
        if ("constructor".equals(item.path("type").asText())) {
          JsonNode inputs = item.path("inputs");
          if (!inputs.isArray()) {
            return List.of();
          }
          List<String> types = new ArrayList<>();
          for (JsonNode input : inputs) {
            String type = input.path("type").asText();
            if (type == null || type.isBlank()) {
              throw new IllegalArgumentException("Constructor input is missing type.");
            }
            types.add(type);
          }
          return types;
        }
      }
      return List.of();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid ABI JSON.", ex);
    }
  }

  static Type<?> toAbiType(String solidityType, String value) {
    return switch (solidityType) {
      case "address" -> new Address(value);
      case "bool" -> new Bool(Boolean.parseBoolean(value));
      case "string" -> new Utf8String(value);
      case "bytes" -> new DynamicBytes(Numeric.hexStringToByteArray(value));
      case "bytes32" -> new Bytes32(Numeric.toBytesPadded(Numeric.toBigInt(value), 32));
      case "uint256" -> new Uint256(new BigInteger(value));
      case "int256" -> new Int256(new BigInteger(value));
      default ->
          throw new IllegalArgumentException(
              "Unsupported constructor type: " + solidityType + ". Supported: address,bool,string,bytes,bytes32,uint256,int256");
    };
  }

  static String buildDeploymentData(String bytecodeRaw, String abiJson, List<String> args) {
    String bytecode = bytecodeRaw.trim();
    if (!Numeric.containsHexPrefix(bytecode)) {
      bytecode = Numeric.prependHexPrefix(bytecode);
    }
    List<String> constructorTypes = constructorTypesFromAbi(abiJson);
    List<String> providedArgs = args == null ? List.of() : args;
    if (constructorTypes.size() != providedArgs.size()) {
      throw new IllegalArgumentException(
          "Constructor argument count mismatch. Expected "
              + constructorTypes.size()
              + " but got "
              + providedArgs.size()
              + ".");
    }
    if (constructorTypes.isEmpty()) {
      return bytecode;
    }

    List<Type> constructorArgs = new ArrayList<>();
    for (int i = 0; i < constructorTypes.size(); i++) {
      constructorArgs.add(toAbiType(constructorTypes.get(i), providedArgs.get(i)));
    }
    String encoded = FunctionEncoder.encodeConstructor(constructorArgs);
    return bytecode + Numeric.cleanHexPrefix(encoded);
  }

  static BigInteger estimateDeployGas(Web3j web3j, String from, String data) {
    try {
      EthEstimateGas estimate =
          web3j
              .ethEstimateGas(Transaction.createContractTransaction(from, null, null, null, BigInteger.ZERO, data))
              .send();
      if (estimate.hasError() || estimate.getAmountUsed() == null) {
        return BigInteger.valueOf(3_000_000L);
      }
      return estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    } catch (Exception ex) {
      return BigInteger.valueOf(3_000_000L);
    }
  }

  static String explorerAddressUrl(ChainProfile chainProfile, String address) {
    String template = chainProfile.explorerAddressUrlTemplate();
    if (template == null || template.isBlank()) {
      return "(explorer URL not configured)";
    }
    if (template.contains("%s")) {
      return String.format(template, address);
    }
    return template.endsWith("/") ? template + address : template + "/" + address;
  }

  static String explorerTxUrl(ChainProfile chainProfile, String txHash) {
    String template = chainProfile.explorerTxUrlTemplate();
    if (template == null || template.isBlank()) {
      return "(explorer URL not configured)";
    }
    if (template.contains("%s")) {
      return String.format(template, txHash);
    }
    return template.endsWith("/") ? template + txHash : template + "/" + txHash;
  }

  static String normalizeHexCount(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      return "0x" + trimmed.substring(2).toLowerCase();
    }
    try {
      return "0x" + new BigInteger(trimmed).toString(16);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid maxCount/number value: " + value);
    }
  }

  static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream()
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }

  static String resolveAlchemyUrl(ChainProfile chainProfile, String apiKey) {
    if (chainProfile.chainId() == 30L) {
      return String.format(ALCHEMY_ROOTSTOCK_MAINNET_URL, apiKey);
    }
    if (chainProfile.chainId() == 31L) {
      return String.format(ALCHEMY_ROOTSTOCK_TESTNET_URL, apiKey);
    }
    throw new IllegalArgumentException(
        "Alchemy asset history is only supported for Rootstock mainnet/testnet.");
  }

  static JsonNode alchemyAssetTransfersRequest(
      String fromBlock,
      String toBlock,
      String fromAddress,
      String toAddress,
      String excludeZeroValue,
      String categoryCsv,
      String maxCountHex,
      String order,
      String contractAddressesCsv,
      String pageKey) {
    var root = OBJECT_MAPPER.createObjectNode();
    root.put("jsonrpc", "2.0");
    root.put("id", 1);
    root.put("method", "alchemy_getAssetTransfers");
    var paramsArray = root.putArray("params");
    var params = paramsArray.addObject();

    if (fromBlock != null && !fromBlock.isBlank()) {
      params.put("fromBlock", fromBlock);
    }
    if (toBlock != null && !toBlock.isBlank()) {
      params.put("toBlock", toBlock);
    }
    if (fromAddress != null && !fromAddress.isBlank()) {
      params.put("fromAddress", fromAddress);
    }
    if (toAddress != null && !toAddress.isBlank()) {
      params.put("toAddress", toAddress);
    }
    if (excludeZeroValue != null && !excludeZeroValue.isBlank()) {
      params.put("excludeZeroValue", Boolean.parseBoolean(excludeZeroValue));
    }
    if (maxCountHex != null && !maxCountHex.isBlank()) {
      params.put("maxCount", maxCountHex);
    }
    if (order != null && !order.isBlank()) {
      params.put("order", order);
    }
    if (pageKey != null && !pageKey.isBlank()) {
      params.put("pageKey", pageKey);
    }

    List<String> categories = splitCsv(categoryCsv);
    if (!categories.isEmpty()) {
      var arr = params.putArray("category");
      categories.forEach(arr::add);
    }

    List<String> contracts = splitCsv(contractAddressesCsv);
    if (!contracts.isEmpty()) {
      var arr = params.putArray("contractAddresses");
      contracts.forEach(arr::add);
    }
    return root;
  }

  static JsonNode postJson(String url, JsonNode body) {
    try {
      HttpClient client = HttpClient.newHttpClient();
      String requestBody = OBJECT_MAPPER.writeValueAsString(body);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("Alchemy HTTP error " + response.statusCode() + ": " + response.body());
      }
      return OBJECT_MAPPER.readTree(response.body());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to call Alchemy asset history API.", ex);
    }
  }

  static String blockscoutVerifyUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return String.format(BLOCKSCOUT_VERIFY_MAINNET_URL, address);
    }
    if (chainProfile.chainId() == 31L) {
      return String.format(BLOCKSCOUT_VERIFY_TESTNET_URL, address);
    }
    throw new IllegalArgumentException("Contract verification is only supported on Rootstock mainnet/testnet.");
  }

  static String blockscoutAddressUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return String.format(BLOCKSCOUT_ADDRESS_MAINNET_URL, address);
    }
    if (chainProfile.chainId() == 31L) {
      return String.format(BLOCKSCOUT_ADDRESS_TESTNET_URL, address);
    }
    return explorerAddressUrl(chainProfile, address);
  }

  static HttpRequest.BodyPublisher multipartBody(
      String boundary, Map<String, String> fields, String fileField, String filename, byte[] fileBytes) {
    List<byte[]> byteArrays = new ArrayList<>();
    String separator = "--" + boundary + "\r\n";

    fields.forEach(
        (name, value) -> {
          String part =
              separator
                  + "Content-Disposition: form-data; name=\""
                  + name
                  + "\"\r\n\r\n"
                  + value
                  + "\r\n";
          byteArrays.add(part.getBytes(StandardCharsets.UTF_8));
        });

    String fileHeader =
        separator
            + "Content-Disposition: form-data; name=\""
            + fileField
            + "\"; filename=\""
            + filename
            + "\"\r\n"
            + "Content-Type: application/json\r\n\r\n";
    byteArrays.add(fileHeader.getBytes(StandardCharsets.UTF_8));
    byteArrays.add(fileBytes);
    byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));

    String ending = "--" + boundary + "--\r\n";
    byteArrays.add(ending.getBytes(StandardCharsets.UTF_8));
    return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
  }

  static String blockscoutContractUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return String.format(BLOCKSCOUT_CONTRACT_MAINNET_URL, address);
    }
    if (chainProfile.chainId() == 31L) {
      return String.format(BLOCKSCOUT_CONTRACT_TESTNET_URL, address);
    }
    throw new IllegalArgumentException("Contract interaction is only supported on Rootstock mainnet/testnet.");
  }

  static JsonNode getJson(String url) {
    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
      }
      return OBJECT_MAPPER.readTree(response.body());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch contract metadata from Blockscout.", ex);
    }
  }

  static String blockscoutAddressApiUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return String.format(BLOCKSCOUT_ADDRESS_API_MAINNET_URL, address);
    }
    if (chainProfile.chainId() == 31L) {
      return String.format(BLOCKSCOUT_ADDRESS_API_TESTNET_URL, address);
    }
    throw new IllegalArgumentException("Blockscout API is only supported on Rootstock mainnet/testnet.");
  }

  static JsonNode fetchContractMetadataWithFallback(ChainProfile chainProfile, String address) {
    try {
      return getJson(blockscoutContractUrl(chainProfile, address));
    } catch (Exception contractEndpointError) {
      JsonNode addressPayload = getJson(blockscoutAddressApiUrl(chainProfile, address));
      JsonNode smartContract = addressPayload.path("smart_contract");
      if (smartContract.isMissingNode() || smartContract.isNull()) {
        if (addressPayload.path("abi").isMissingNode()) {
          throw new IllegalStateException(
              "Unable to fetch contract metadata from Blockscout.", contractEndpointError);
        }
        return addressPayload;
      }
      return smartContract;
    }
  }

  static JsonNode findAbiNodeInPayload(JsonNode payload) {
    if (payload == null || payload.isMissingNode() || payload.isNull()) {
      return null;
    }
    JsonNode directAbi = payload.path("abi");
    if (!directAbi.isMissingNode() && !directAbi.isNull()) {
      return directAbi;
    }
    JsonNode smartContractAbi = payload.path("smart_contract").path("abi");
    if (!smartContractAbi.isMissingNode() && !smartContractAbi.isNull()) {
      return smartContractAbi;
    }
    JsonNode contractAbi = payload.path("contract").path("abi");
    if (!contractAbi.isMissingNode() && !contractAbi.isNull()) {
      return contractAbi;
    }
    JsonNode resultAbi = payload.path("result").path("abi");
    if (!resultAbi.isMissingNode() && !resultAbi.isNull()) {
      return resultAbi;
    }
    return null;
  }

  static JsonNode resolveAbiArrayFromBlockscout(ChainProfile chainProfile, String address) {
    List<String> urls =
        List.of(
            blockscoutContractUrl(chainProfile, address),
            blockscoutAddressApiUrl(chainProfile, address));
    Exception lastError = null;
    for (String url : urls) {
      try {
        JsonNode payload = getJson(url);
        JsonNode abiNode = findAbiNodeInPayload(payload);
        if (abiNode == null) {
          continue;
        }
        if (abiNode.isArray()) {
          return abiNode;
        }
        if (abiNode.isTextual()) {
          JsonNode parsed = OBJECT_MAPPER.readTree(abiNode.asText());
          if (parsed.isArray()) {
            return parsed;
          }
        }
      } catch (Exception ex) {
        lastError = ex;
      }
    }
    throw new IllegalStateException("Unable to resolve contract ABI from Blockscout.", lastError);
  }

  static JsonNode extractAbiNode(JsonNode contractJson) {
    JsonNode abiNode = contractJson.path("abi");
    if (abiNode.isArray()) {
      return abiNode;
    }
    if (abiNode.isTextual()) {
      try {
        return OBJECT_MAPPER.readTree(abiNode.asText());
      } catch (Exception ex) {
        throw new IllegalStateException("Invalid ABI payload from explorer.", ex);
      }
    }
    throw new IllegalStateException("Explorer response does not contain contract ABI.");
  }

  static Type<?> toAbiInputType(String solidityType, String value) {
    if ("bytes[]".equals(solidityType)) {
      List<DynamicBytes> items =
          splitCsv(value).stream()
              .map(Numeric::hexStringToByteArray)
              .map(DynamicBytes::new)
              .toList();
      return new DynamicArray<>(DynamicBytes.class, items);
    }
    if ("bytes32[]".equals(solidityType)) {
      List<Bytes32> items =
          splitCsv(value).stream()
              .map(v -> new Bytes32(Numeric.toBytesPadded(Numeric.toBigInt(v), 32)))
              .toList();
      return new DynamicArray<>(Bytes32.class, items);
    }
    return switch (solidityType) {
      case "address" -> new Address(value);
      case "bool" -> new Bool(Boolean.parseBoolean(value));
      case "string" -> new Utf8String(value);
      case "bytes" -> new DynamicBytes(Numeric.hexStringToByteArray(value));
      case "bytes32" -> new Bytes32(Numeric.toBytesPadded(Numeric.toBigInt(value), 32));
      case "uint256", "uint" -> new Uint256(new BigInteger(value));
      case "uint8" -> new Uint8(new BigInteger(value));
      case "int256", "int", "int64" -> new Int256(new BigInteger(value));
      default ->
          throw new IllegalArgumentException(
              "Unsupported input type: "
                  + solidityType
                  + ". Supported: address,bool,string,bytes,bytes32,bytes[],bytes32[],uint8,uint,uint256,int,int64,int256");
    };
  }

  static TypeReference<?> outputTypeReference(String solidityType) {
    return switch (solidityType) {
      case "address" -> TypeReference.create(Address.class);
      case "bool" -> TypeReference.create(Bool.class);
      case "string" -> TypeReference.create(Utf8String.class);
      case "bytes" -> TypeReference.create(DynamicBytes.class);
      case "bytes32" -> TypeReference.create(Bytes32.class);
      case "uint256", "uint" -> TypeReference.create(Uint256.class);
      case "uint8" -> TypeReference.create(Uint8.class);
      case "int256", "int", "int64" -> TypeReference.create(Int256.class);
      default ->
          throw new IllegalArgumentException(
              "Unsupported output type: "
                  + solidityType
                  + ". Supported: address,bool,string,bytes,bytes32,uint8,uint,uint256,int,int64,int256");
    };
  }

  static JsonNode readAbiArrayResource(String resourceName) {
    try (var in = EvmCliCommand.class.getClassLoader().getResourceAsStream(resourceName)) {
      if (in == null) {
        throw new IllegalStateException("ABI resource not found: " + resourceName);
      }
      JsonNode root = OBJECT_MAPPER.readTree(in);
      if (!root.isArray()) {
        throw new IllegalStateException("ABI resource must be a JSON array: " + resourceName);
      }
      return root;
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read ABI resource: " + resourceName, ex);
    }
  }

  static String readableTypeValue(Type type) {
    if (type instanceof Utf8String s) {
      return s.getValue();
    }
    if (type instanceof Address a) {
      return a.getValue();
    }
    if (type instanceof Bool b) {
      return Boolean.toString(b.getValue());
    }
    return type.getValue() == null ? "null" : type.getValue().toString();
  }

  static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  static String cWarn(String text) {
    return Ansi.ansi().fg(Ansi.Color.YELLOW).a(text).reset().toString();
  }

  static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  static BigInteger decimalToUnits(BigDecimal value, int decimals) {
    try {
      return value.movePointRight(decimals).toBigIntegerExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException(
          "Too many decimal places for token decimals=" + decimals + ": " + value, ex);
    }
  }

  static BigDecimal unitsToDecimal(BigInteger units, int decimals) {
    return new BigDecimal(units).movePointLeft(decimals);
  }

  static TransactionReceipt waitForReceipt(Web3j web3j, String txHash, int maxPolls, long sleepMs)
      throws Exception {
    for (int i = 0; i < maxPolls; i++) {
      EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
      if (receiptResponse.getTransactionReceipt().isPresent()) {
        return receiptResponse.getTransactionReceipt().get();
      }
      Thread.sleep(sleepMs);
    }
    throw new IllegalStateException("Timed out waiting for transaction receipt: " + txHash);
  }

  static List<Type> ethCall(Web3j web3j, String from, String to, Function function) throws Exception {
    String encoded = FunctionEncoder.encode(function);
    var response =
        web3j.ethCall(
                Transaction.createEthCallTransaction(from, to, encoded),
                DefaultBlockParameterName.LATEST)
            .send();
    if (response.hasError()) {
      throw new IllegalStateException(response.getError().getMessage());
    }
    return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
  }

  static class NetworkSelector {
    @Option(names = "--mainnet", description = "Use chains.mainnet")
    boolean mainnet;

    @Option(names = "--testnet", description = "Use chains.testnet")
    boolean testnet;

    @Option(
        names = "--chain",
        description = "Use config chain key, e.g. chains.custom.<name> or <name>")
    String chain;

    @Option(names = "--chainurl", description = "Use an explicit RPC URL")
    String chainUrl;
  }

  static class NetworkOptions {
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkSelector selector = new NetworkSelector();
  }

  static abstract class HelpCommand {
    @Option(
        names = {"-h", "--help"},
        usageHelp = true,
        description = "Show this help message and exit.")
    boolean helpRequested;
  }

  @Command(name = "version", description = "Print CLI version")
  static class VersionCommand extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("evm-cli " + cliVersion());
      return 0;
    }
  }

  @Command(
      name = "wallet",
      description = "Wallet management",
      subcommands = {
        WalletCreate.class,
        WalletImport.class,
        WalletList.class,
        WalletActive.class,
        WalletDump.class,
        WalletSwitch.class,
        WalletRename.class,
        WalletDelete.class,
        WalletBackup.class,
        WalletAddressBook.class
      })
  static class WalletCommand extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      throw new IllegalArgumentException(
          "Missing wallet subcommand. Use --help for available wallet commands.");
    }
  }

  @Command(name = "create", description = "Create wallet")
  static class WalletCreate extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Wallet name")
    String name;

    @Override
    public Integer call() {
      char[] password = readPassword("Wallet password: ");
      WalletMetadata wallet = ctx.walletService().create(name, password);
      System.out.printf("Created wallet %s (%s)%n", wallet.name(), wallet.address());
      return 0;
    }
  }

  @Command(
      name = "import",
      description = "Import wallet from private key")
  static class WalletImport extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Wallet name")
    String name;

    @Option(names = "--privkey", required = true, description = "Hex private key")
    String privateKey;

    @Override
    public Integer call() {
      char[] password = readPassword("Wallet password: ");
      WalletMetadata wallet = ctx.walletService().importPrivateKey(name, privateKey, password);
      System.out.printf("Imported wallet %s (%s)%n", wallet.name(), wallet.address());
      return 0;
    }
  }

  @Command(name = "list", description = "List wallets")
  static class WalletList extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      List<WalletMetadata> wallets = ctx.walletService().list();
      if (wallets.isEmpty()) {
        System.out.println("No wallets found.");
        return 0;
      }
      wallets.forEach(
          w ->
              System.out.printf(
                  "- %s %s%n",
                  Ansi.ansi().fg(Ansi.Color.GREEN).a(w.name()).reset(),
                  w.address()));
      return 0;
    }
  }

  @Command(name = "active", description = "Show active wallet")
  static class WalletActive extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      ctx.walletService()
          .active()
          .ifPresentOrElse(
              w -> System.out.printf("%s %s%n", w.name(), w.address()),
              () -> System.out.println("No active wallet."));
      return 0;
    }
  }

  @Command(name = "dump", description = "Reveal wallet private key")
  static class WalletDump extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Wallet name (defaults to active wallet)")
    String name;

    @Override
    public Integer call() {
      String walletName = name;
      if (walletName == null || walletName.isBlank()) {
        walletName =
            ctx.walletService()
                .active()
                .map(WalletMetadata::name)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "No active wallet found. Provide a wallet name."));
      }
      char[] password = readPassword("Wallet password: ");
      String privateKey = ctx.walletService().dumpPrivateKey(walletName, password);
      System.out.println(privateKey);
      return 0;
    }
  }

  @Command(name = "switch", description = "Switch active wallet")
  static class WalletSwitch extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Wallet name")
    String name;

    @Override
    public Integer call() {
      ctx.walletService().switchActive(name);
      System.out.println("Active wallet switched to " + name);
      return 0;
    }
  }

  @Command(name = "rename", description = "Rename wallet")
  static class WalletRename extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Old name")
    String oldName;

    @Parameters(index = "1", description = "New name")
    String newName;

    @Override
    public Integer call() {
      ctx.walletService().rename(oldName, newName);
      System.out.printf("Renamed wallet %s -> %s%n", oldName, newName);
      return 0;
    }
  }

  @Command(name = "delete", description = "Delete wallet")
  static class WalletDelete extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Wallet name")
    String name;

    @Override
    public Integer call() {
      ctx.walletService().delete(name);
      System.out.println("Deleted wallet " + name);
      return 0;
    }
  }

  @Command(name = "backup", description = "Backup wallet")
  static class WalletBackup extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Wallet backup TUI placeholder.");
      return 0;
    }
  }

  @Command(
      name = "address-book", description = "Address book TUI")
  static class WalletAddressBook extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Address book TUI placeholder.");
      return 0;
    }
  }

  @Command(name = "config", description = "Config UI")
  static class ConfigCommand extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      CliConfig config = ctx.configPort().load();
      ensureConfigShape(config);
      LineReader reader = LineReaderBuilder.builder().build();
      boolean dirty = false;

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
            config.getChains().setMainnet(promptChainProfile(reader, "mainnet", config.getChains().getMainnet()));
            dirty = true;
          }
          case "2" -> {
            config.getChains().setTestnet(promptChainProfile(reader, "testnet", config.getChains().getTestnet()));
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
                "Wallet password cache is now "
                    + (!current ? "enabled" : "disabled")
                    + ".");
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
            ctx.configPort().save(config);
            System.out.println("Config saved.");
            return 0;
          }
          case "8" -> {
            if (dirty
                && !promptBoolean(
                    reader, "Discard unsaved changes and exit", false)) {
              break;
            }
            return 0;
          }
          default -> System.out.println("Unknown option.");
        }
      }
    }
  }

  @Command(name = "balance", description = "Get native balance")
  static class BalanceCommand extends HelpCommand implements Callable<Integer> {
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    Target target;

    @Option(names = "--rns", description = "RNS target")
    String rns;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    static class Target {
      @Option(names = "--wallet")
      String wallet;

      @Option(names = "--address", description = "Address or RNS name")
      String address;
    }

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      String address;
      if (rns != null) {
        address = resolveAddressInput(chainProfile, rns);
      } else {
        if (target == null) {
          WalletMetadata activeWallet =
              ctx.walletService()
                  .active()
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "Provide --wallet, --address, or --rns, or set an active wallet."));
          address = activeWallet.address();
        } else if (target.address != null) {
          address = resolveAddressInput(chainProfile, target.address);
        } else if (target.wallet != null) {
          WalletMetadata wallet =
              ctx.walletService().list().stream()
                  .filter(w -> w.name().equals(target.wallet))
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalArgumentException("Wallet not found: " + target.wallet));
          address = wallet.address();
        } else {
          WalletMetadata activeWallet =
              ctx.walletService()
                  .active()
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "Provide --wallet, --address, or --rns, or set an active wallet."));
          address = activeWallet.address();
        }
      }
      BigInteger wei;
      try {
        wei = ctx.balanceService().nativeBalanceWei(chainProfile, address);
      } catch (Exception ex) {
        throw new IllegalStateException(
            "Unable to fetch balance on network '"
                + chainProfile.name()
                + "'. Try --testnet, --mainnet, --chain chains.custom.<name>, or --chainurl <rpcUrl>.",
            ex);
      }
      String amountDisplay =
          Ansi.ansi()
              .fg(Ansi.Color.GREEN)
              .a(
                  String.format(
                      "%s wei (%s %s)",
                      wei,
                      ctx.balanceService().toNative(wei).toPlainString(),
                      chainProfile.nativeSymbol()))
              .reset()
              .toString();
      System.out.printf(
          "%s balance on %s: %s%n",
          address,
          chainProfile.name(),
          amountDisplay);
      return 0;
    }
  }

  @Command(
      name = "transfer", description = "Send native transfer")
  static class TransferCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = "--wallet", description = "Wallet name (defaults to active wallet)")
    String wallet;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    ToTarget toTarget;

    @Option(names = "--token", description = "ERC-20 token address")
    String token;

    @Option(names = "--value", description = "Amount to transfer (RBTC or token units)")
    BigDecimal value;

    @Option(names = "--gas-limit", description = "Gas limit override")
    BigInteger gasLimit;

    @Option(names = "--gas-price", description = "Gas price in wei override")
    BigInteger gasPrice;

    @Option(names = "--priority-fee", description = "Extra gwei added to gas price")
    BigDecimal priorityFeeGwei;

    @Option(names = "--data", defaultValue = "")
    String data;

    @Option(names = {"-i", "--interactive"})
    boolean interactive;

    @Option(names = "--attest-transfer", description = "Create transfer attestation (placeholder)")
    boolean attestTransfer;

    @Option(names = "--attest-reason", description = "Attestation reason (placeholder)")
    String attestReason;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    static class ToTarget {
      @Option(names = "--address", description = "Address or RNS name")
      String address;

      @Option(names = "--rns")
      String rns;
    }

    private String resolveWalletName() {
      if (wallet != null && !wallet.isBlank()) {
        return wallet;
      }
      return ctx.walletService()
          .active()
          .map(WalletMetadata::name)
          .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
    }

    private String resolveRecipient(ChainProfile chainProfile) {
      if (interactive && toTarget == null) {
        String recipientRaw = readRequiredTextPrompt(cInfo("Recipient address or RNS"), "");
        return resolveAddressInput(chainProfile, recipientRaw);
      }
      if (toTarget == null) {
        throw new IllegalArgumentException("Provide one of --address or --rns.");
      }
      return toTarget.address != null
          ? resolveAddressInput(chainProfile, toTarget.address)
          : resolveAddressInput(chainProfile, toTarget.rns);
    }

    private BigDecimal resolveAmount(String unitLabel) {
      if (value != null) {
        return value;
      }
      if (!interactive) {
        throw new IllegalArgumentException("Provide --value.");
      }
      while (true) {
        String raw = readRequiredTextPrompt(cInfo("Amount (" + unitLabel + ")"), "");
        try {
          BigDecimal parsed = new BigDecimal(raw);
          if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Amount must be greater than zero.");
            continue;
          }
          return parsed;
        } catch (NumberFormatException ex) {
          System.out.println("Please enter a valid decimal amount.");
        }
      }
    }

    private BigInteger effectiveGasPrice(Web3j web3j) throws Exception {
      BigInteger base = gasPrice != null ? gasPrice : web3j.ethGasPrice().send().getGasPrice();
      if (priorityFeeGwei == null) {
        return base;
      }
      BigInteger extra = decimalToUnits(priorityFeeGwei, 9);
      return base.add(extra);
    }

    private String attestationPayloadJson(
        ChainProfile chainProfile,
        String transferTxHash,
        String from,
        String to,
        String asset,
        BigDecimal amount,
        String reason) {
      String safeReason = reason == null ? "" : reason.replace("\"", "\\\"");
      return "{"
          + "\"type\":\"transfer\","
          + "\"version\":1,"
          + "\"network\":\""
          + chainProfile.name()
          + "\","
          + "\"transferTxHash\":\""
          + transferTxHash
          + "\","
          + "\"from\":\""
          + from
          + "\","
          + "\"to\":\""
          + to
          + "\","
          + "\"asset\":\""
          + asset
          + "\","
          + "\"amount\":\""
          + amount.toPlainString()
          + "\","
          + "\"reason\":\""
          + safeReason
          + "\","
          + "\"timestamp\":\""
          + Instant.now()
          + "\""
          + "}";
    }

    private String submitAttestation(
        Web3j web3j,
        ChainProfile chainProfile,
        Credentials credentials,
        String transferTxHash,
        String recipient,
        String asset,
        BigDecimal amount)
        throws Exception {
      String reason = attestReason == null ? "" : attestReason;
      String jsonPayload =
          attestationPayloadJson(
              chainProfile,
              transferTxHash,
              credentials.getAddress(),
              recipient,
              asset,
              amount,
              reason);
      String dataHex = Numeric.toHexString(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      EthGetTransactionCount nonceResponse =
          web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING).send();
      BigInteger nonce = nonceResponse.getTransactionCount();
      BigInteger txGasPrice = effectiveGasPrice(web3j);
      BigInteger txGasLimit = gasLimit != null ? gasLimit : BigInteger.valueOf(120_000L);

      RawTransaction attestationTx =
          RawTransaction.createTransaction(
              nonce,
              txGasPrice,
              txGasLimit,
              credentials.getAddress(),
              BigInteger.ZERO,
              dataHex);
      byte[] signed = TransactionEncoder.signMessage(attestationTx, chainProfile.chainId(), credentials);
      EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
      if (sent.hasError()) {
        throw new IllegalStateException("Attestation failed: " + sent.getError().getMessage());
      }
      String txHash = sent.getTransactionHash();
      TransactionReceipt receipt = waitForReceipt(web3j, txHash, 120, 2000L);
      if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
        throw new IllegalStateException("Attestation failed. Receipt status: " + receipt.getStatus());
      }
      return txHash;
    }

    private void executeNativeTransfer(
        ChainProfile chainProfile,
        Credentials credentials,
        String recipient,
        BigDecimal amountRbtc)
        throws Exception {
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        BigInteger amountWei = decimalToUnits(amountRbtc, 18);
        BigInteger balanceWei =
            web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
        BigDecimal balanceRbtc = unitsToDecimal(balanceWei, 18);

        System.out.println(cInfo("📄 Wallet Address: ") + credentials.getAddress());
        System.out.println(cInfo("🎯 Recipient Address: ") + recipient);
        System.out.println(
            cInfo("💵 Amount to Transfer: ")
                + amountRbtc.toPlainString()
                + " "
                + chainProfile.nativeSymbol());
        System.out.println(
            cInfo("💰 Current Balance: ")
                + balanceRbtc.toPlainString()
                + " "
                + chainProfile.nativeSymbol());

        EthGetTransactionCount nonceResponse =
            web3j
                .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                .send();
        BigInteger nonce = nonceResponse.getTransactionCount();
        BigInteger txGasPrice = effectiveGasPrice(web3j);
        BigInteger txGasLimit = gasLimit != null ? gasLimit : BigInteger.valueOf(21_000L);

        RawTransaction tx =
            RawTransaction.createTransaction(
                nonce,
                txGasPrice,
                txGasLimit,
                recipient,
                amountWei,
                data == null ? "" : data);
        byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (sent.hasError()) {
          throw new IllegalStateException(sent.getError().getMessage());
        }

        String txHash = sent.getTransactionHash();
        System.out.println(cWarn("🔄 Transaction initiated. TxHash: ") + txHash);
        TransactionReceipt receipt = waitForReceipt(web3j, txHash, 120, 2000L);
        if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
          throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
        }
        System.out.println(cOk("✅ Transaction confirmed successfully!"));
        System.out.println(cInfo("📦 Block Number: ") + receipt.getBlockNumber());
        System.out.println(cInfo("⛽ Gas Used: ") + receipt.getGasUsed());
        System.out.println(cInfo("🔗 View on Explorer: ") + explorerTxUrl(chainProfile, txHash));

        if (attestTransfer) {
          System.out.println(cWarn("📝 Creating on-chain attestation..."));
          String attestationTxHash =
              submitAttestation(
                  web3j,
                  chainProfile,
                  credentials,
                  txHash,
                  recipient,
                  chainProfile.nativeSymbol(),
                  amountRbtc);
          System.out.println(cOk("✅ Attestation confirmed!"));
          System.out.println(cInfo("🔑 Attestation TxHash: ") + attestationTxHash);
          System.out.println(cInfo("🔗 View on Explorer: ") + explorerTxUrl(chainProfile, attestationTxHash));
        }
      }
    }

    private void executeErc20Transfer(
        ChainProfile chainProfile,
        Credentials credentials,
        String recipient,
        BigDecimal amount)
        throws Exception {
      if (!isHexAddress(token)) {
        throw new IllegalArgumentException("Invalid token address: " + token);
      }
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        String walletAddress = credentials.getAddress();
        Function nameFn =
            new Function("name", List.of(), List.of(TypeReference.create(Utf8String.class)));
        Function symbolFn =
            new Function("symbol", List.of(), List.of(TypeReference.create(Utf8String.class)));
        Function decimalsFn =
            new Function("decimals", List.of(), List.of(TypeReference.create(Uint8.class)));

        String tokenName = "Unknown";
        String tokenSymbol = "TOKEN";
        int decimals = 18;
        try {
          List<Type> outName = ethCall(web3j, walletAddress, token, nameFn);
          if (!outName.isEmpty()) {
            tokenName = ((Utf8String) outName.get(0)).getValue();
          }
          List<Type> outSymbol = ethCall(web3j, walletAddress, token, symbolFn);
          if (!outSymbol.isEmpty()) {
            tokenSymbol = ((Utf8String) outSymbol.get(0)).getValue();
          }
          List<Type> outDecimals = ethCall(web3j, walletAddress, token, decimalsFn);
          if (!outDecimals.isEmpty()) {
            decimals = ((Uint8) outDecimals.get(0)).getValue().intValue();
          }
        } catch (Exception ignored) {
        }

        BigInteger amountUnits = decimalToUnits(amount, decimals);
        Function transferFn =
            new Function(
                "transfer",
                List.of(new Address(recipient), new Uint256(amountUnits)),
                List.of(TypeReference.create(Bool.class)));
        String transferData =
            (data != null && !data.isBlank()) ? data : FunctionEncoder.encode(transferFn);

        // Simulate transfer call before sending.
        try {
          ethCall(web3j, walletAddress, token, transferFn);
          System.out.println(cOk("✔ ✅ Simulation successful, proceeding with transfer..."));
        } catch (Exception ex) {
          throw new IllegalStateException("Simulation failed: " + ex.getMessage(), ex);
        }

        EthGetTransactionCount nonceResponse =
            web3j.ethGetTransactionCount(walletAddress, DefaultBlockParameterName.PENDING).send();
        BigInteger nonce = nonceResponse.getTransactionCount();
        BigInteger txGasPrice = effectiveGasPrice(web3j);
        BigInteger txGasLimit = gasLimit != null ? gasLimit : BigInteger.valueOf(65_000L);

        RawTransaction tx =
            RawTransaction.createTransaction(
                nonce, txGasPrice, txGasLimit, token, BigInteger.ZERO, transferData);
        byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (sent.hasError()) {
          throw new IllegalStateException(sent.getError().getMessage());
        }
        String txHash = sent.getTransactionHash();

        System.out.println(cInfo("🔑 Wallet account: ") + walletAddress);
        System.out.println(cEmph("📄 Token Information:"));
        System.out.println(cInfo("     Name: ") + tokenName);
        System.out.println(cInfo("     Symbol: ") + tokenSymbol);
        System.out.println(cInfo("     Contract: ") + token);
        System.out.println(cInfo("🎯 To Address: ") + recipient);
        System.out.println(cInfo("💵 Amount to Transfer: ") + amount.toPlainString() + " " + tokenSymbol);
        System.out.println(cWarn("🔄 Transaction initiated. TxHash: ") + txHash);

        TransactionReceipt receipt = waitForReceipt(web3j, txHash, 120, 2000L);
        if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
          throw new IllegalStateException("Transfer failed. Receipt status: " + receipt.getStatus());
        }
        System.out.println(cOk("✅ Transfer completed successfully!"));
        System.out.println(cInfo("📦 Block Number: ") + receipt.getBlockNumber());
        System.out.println(cInfo("⛽ Gas Used: ") + receipt.getGasUsed());
        System.out.println(cInfo("🔗 View on Explorer: ") + explorerTxUrl(chainProfile, txHash));

        if (attestTransfer) {
          System.out.println(cWarn("📝 Creating on-chain attestation..."));
          String attestationTxHash =
              submitAttestation(web3j, chainProfile, credentials, txHash, recipient, token, amount);
          System.out.println(cOk("✅ Attestation confirmed!"));
          System.out.println(cInfo("🔑 Attestation TxHash: ") + attestationTxHash);
          System.out.println(cInfo("🔗 View on Explorer: ") + explorerTxUrl(chainProfile, attestationTxHash));
        }
      }
    }

    @Override
    public Integer call() {
      if (!attestTransfer && attestReason != null && !attestReason.isBlank()) {
        throw new IllegalArgumentException("--attest-reason requires --attest-transfer.");
      }
      ChainProfile chainProfile = resolveChain(networkOptions);
      if (interactive) {
        System.out.println(cEmph("🚀 Interactive Transfer"));
        System.out.println(cWarn("Provide recipient as address or RNS, then amount."));
      }
      String recipient = resolveRecipient(chainProfile);
      String walletName = resolveWalletName();
      if (interactive && (token == null || token.isBlank())) {
        String raw =
            readTextPrompt(cInfo("Token contract address (leave empty for RBTC)"), "");
        token = raw == null ? null : raw.trim();
      }
      BigDecimal amount =
          resolveAmount((token == null || token.isBlank()) ? chainProfile.nativeSymbol() : "token");
      char[] password = readPassword("Wallet password: ");
      String privateKeyHex = ctx.walletService().dumpPrivateKey(walletName, password);
      Credentials credentials = Credentials.create(privateKeyHex);

      try {
        if (token == null || token.isBlank()) {
          executeNativeTransfer(chainProfile, credentials, recipient, amount);
        } else {
          executeErc20Transfer(chainProfile, credentials, recipient, amount);
        }
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to complete transfer.", ex);
      }
    }
  }

  @Command(name = "tx", description = "Transaction status")
  static class TxCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = "--txid", required = true)
    String txid;

    @Option(names = "--monitor")
    boolean monitor;

    @Option(names = "--confirmations", defaultValue = "1")
    int confirmations;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      if (monitor) {
        MonitorSession session =
            ctx.monitorManager().startTxConfirmations(chainProfile.name(), txid, 10, confirmations);
        System.out.println("Monitor session started: " + session.getId());
        return 0;
      }
      var status = ctx.txService().receiptStatus(chainProfile, txid);
      System.out.println(status.map(s -> "Receipt status: " + s).orElse("Receipt not found yet."));
      return 0;
    }
  }

  @Command(name = "monitor", description = "Monitor sessions")
  static class MonitorCommand extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Session id to open monitor viewer")
    UUID sessionId;

    @Option(names = "--list")
    boolean list;

    @Option(names = "--stop")
    UUID stop;

    @Option(names = "--address")
    String address;

    @Option(names = "--balance")
    boolean balance;

    @Option(names = "--transactions")
    boolean transactions;

    @Option(names = "--tx")
    String tx;

    @Option(names = "--confirmations", defaultValue = "1")
    int confirmations;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      if (sessionId != null) {
        monitorSessionTui(sessionId);
        return 0;
      }
      if (list) {
        ctx.monitorManager()
            .list()
            .forEach(
                s -> System.out.printf("%s %s %s%n", s.getId(), s.getType(), s.getStatus()));
        return 0;
      }
      if (stop != null) {
        ctx.monitorManager().stop(stop);
        System.out.println("Stopped session " + stop);
        return 0;
      }
      if (tx != null) {
        ChainProfile chainProfile = resolveChain(networkOptions);
        MonitorSession session =
            ctx.monitorManager().startTxConfirmations(chainProfile.name(), tx, 10, confirmations);
        System.out.println("Started tx monitor session " + session.getId());
        return 0;
      }
      System.out.println("Use --list, --stop, --tx, or provide <session-id>.");
      return 0;
    }
  }

  @Command(name = "resolve", description = "Resolve name")
  static class ResolveCommand extends HelpCommand implements Callable<Integer> {
    @Parameters(index = "0")
    String name;

    @Option(names = "--reverse")
    boolean reverse;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      if (reverse) {
        if (!isHexAddress(name)) {
          throw new IllegalArgumentException(
              "Invalid address format for reverse resolution: " + name);
        }
        String resolved =
            lookup
                .reverseLookup(chainProfile.rpcUrl(), name)
                .orElseThrow(() -> new IllegalArgumentException("No RNS name found for: " + name));
        System.out.println(resolved);
        return 0;
      }

      if (!isValidRnsName(name)) {
        throw new IllegalArgumentException(
            "Invalid RNS format: " + name + ". Expected domain-like format, e.g. alice.rsk");
      }
      String resolved =
          lookup
              .lookup(chainProfile.rpcUrl(), name)
              .orElseThrow(() -> new IllegalArgumentException("RNS name not found: " + name));
      System.out.println(resolved);
      return 0;
    }
  }

  @Command(name = "deploy", description = "Deploy contract")
  static class DeployCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = "--abi", required = true, description = "Path to ABI JSON file")
    String abi;

    @Option(names = "--bytecode", required = true, description = "Path to bytecode file (.bin)")
    String bytecode;

    @Option(names = "--wallet", description = "Wallet name (defaults to active wallet)")
    String wallet;

    @Option(names = "--args", arity = "0..*", description = "Constructor arguments")
    List<String> args;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      System.out.printf("🔧 Initializing provider for %s...%n", chainProfile.name());

      String walletName = wallet;
      if (walletName == null || walletName.isBlank()) {
        walletName =
            ctx.walletService()
                .active()
                .map(WalletMetadata::name)
                .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
      }

      char[] password = readPassword("? Enter your password to decrypt the wallet: ");
      String privateKeyHex = ctx.walletService().dumpPrivateKey(walletName, password);
      Credentials credentials = Credentials.create(privateKeyHex);
      System.out.println("🔑 Wallet account: " + credentials.getAddress());

      System.out.println("📄 Reading ABI from " + abi + "...");
      String abiContent = readRequiredFile(abi, "ABI");
      System.out.println("📄 Reading Bytecode from " + bytecode + "...");
      String bytecodeContent = readRequiredFile(bytecode, "bytecode");
      String deploymentData = buildDeploymentData(bytecodeContent, abiContent, args);

      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        EthGetTransactionCount nonceResponse =
            web3j
                .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                .send();
        BigInteger nonce = nonceResponse.getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = estimateDeployGas(web3j, credentials.getAddress(), deploymentData);

        RawTransaction tx =
            RawTransaction.createContractTransaction(
                nonce, gasPrice, gasLimit, BigInteger.ZERO, deploymentData);
        byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
        String payload = Numeric.toHexString(signed);

        EthSendTransaction sent = web3j.ethSendRawTransaction(payload).send();
        if (sent.hasError()) {
          throw new IllegalStateException(sent.getError().getMessage());
        }

        String txHash = sent.getTransactionHash();
        System.out.println("✔ 🎉 Contract deployment transaction sent!");
        System.out.println("🔑 Transaction Hash: " + txHash);

        String contractAddress = null;
        String status = null;
        for (int i = 0; i < 120; i++) {
          EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
          if (receiptResponse.getTransactionReceipt().isPresent()) {
            var receipt = receiptResponse.getTransactionReceipt().get();
            status = receipt.getStatus();
            contractAddress = receipt.getContractAddress();
            break;
          }
          Thread.sleep(2000L);
        }

        if (contractAddress == null || status == null) {
          throw new IllegalStateException("Timed out waiting for deployment receipt.");
        }
        if (!"0x1".equalsIgnoreCase(status)) {
          throw new IllegalStateException("Deployment failed. Receipt status: " + status);
        }

        System.out.println("✔ 📜 Contract deployed successfully!");
        System.out.println("📍 Contract Address: " + contractAddress);
        System.out.println("🔗 View on Explorer: " + explorerAddressUrl(chainProfile, contractAddress));
        return 0;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for deployment receipt.", ex);
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to deploy contract.", ex);
      }
    }
  }

  @Command(name = "verify", description = "Verify contract")
  static class VerifyCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = "--json", required = true, description = "Path to Standard JSON Input file")
    String json;

    @Option(names = "--name", required = true, description = "Contract name")
    String name;

    @Option(names = "--address", required = true, description = "Deployed contract address")
    String address;

    @Option(names = "--compiler-version", required = true, description = "Compiler version, e.g. v0.8.17+commit...")
    String compilerVersion;

    @Option(names = "--license-type", defaultValue = "mit")
    String licenseType;

    @Option(names = "--autodetect-constructor-args", defaultValue = "true")
    boolean autodetectConstructorArgs;

    @Option(names = "--constructor-args", description = "Hex-encoded constructor args if autodetect is false")
    String constructorArgs;

    @Option(names = "--decodedArgs", description = "Deprecated alias. Use --constructor-args hex value")
    List<String> decodedArgs;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      if (!isHexAddress(address)) {
        throw new IllegalArgumentException("Invalid contract address: " + address);
      }
      if (!autodetectConstructorArgs) {
        boolean hasConstructorArgs = constructorArgs != null && !constructorArgs.isBlank();
        boolean hasDecodedArgs = decodedArgs != null && !decodedArgs.isEmpty();
        if (!hasConstructorArgs && hasDecodedArgs) {
          throw new IllegalArgumentException(
              "--decodedArgs is not supported by Blockscout standard-input verify. Provide --constructor-args (hex).");
        }
        if (!hasConstructorArgs) {
          throw new IllegalArgumentException(
              "When --autodetect-constructor-args=false, --constructor-args is required.");
        }
      }

      ChainProfile chainProfile = resolveChain(networkOptions);
      String endpoint = blockscoutVerifyUrl(chainProfile, address);
      byte[] jsonFile;
      try {
        jsonFile = Files.readAllBytes(Path.of(json));
      } catch (IOException ex) {
        throw new IllegalArgumentException("Unable to read JSON file: " + json, ex);
      }

      System.out.printf("🔧 Initializing verification on %s...%n", chainProfile.name());
      System.out.println("📄 Reading JSON Standard Input from " + json + "...");
      System.out.println("🔎 Verifying contract " + name + " deployed at " + address + "...");
      if (!autodetectConstructorArgs) {
        System.out.println("📄 Using constructor arguments: " + constructorArgs);
      }

      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("compiler_version", compilerVersion);
      fields.put("contract_name", name);
      fields.put("license_type", licenseType);
      fields.put("autodetect_constructor_args", Boolean.toString(autodetectConstructorArgs));
      if (!autodetectConstructorArgs && constructorArgs != null && !constructorArgs.isBlank()) {
        fields.put("constructor_args", constructorArgs);
      }

      String boundary = "----evmcli-" + UUID.randomUUID();
      HttpRequest.BodyPublisher body =
          multipartBody(
              boundary,
              fields,
              "files[0]",
              Path.of(json).getFileName().toString(),
              jsonFile);

      try {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body)
                .build();
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          throw new IllegalStateException(
              "Verification API error " + response.statusCode() + ": " + response.body());
        }
        System.out.println("✔ 🎉 Contract verification request sent!");
        System.out.println("✔ 📜 Verification submitted successfully!");
        System.out.println("🔗 View on Explorer: " + blockscoutAddressUrl(chainProfile, address));
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to verify contract.", ex);
      }
    }
  }

  @Command(
      name = "contract",
      description = "Interactive contract mode")
  static class ContractCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = "--address", required = true)
    String address;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      if (!isHexAddress(address)) {
        throw new IllegalArgumentException("Invalid contract address: " + address);
      }

      ChainProfile chainProfile = resolveChain(networkOptions);
      System.out.printf("🔧 Initializing interaction on %s...%n", chainProfile.name());
      System.out.println("🔎 Checking if contract " + address + " is verified...");

      JsonNode abiArray = resolveAbiArrayFromBlockscout(chainProfile, address);
      List<JsonNode> readFunctions =
          new ArrayList<>();
      for (JsonNode entry : abiArray) {
        if (!"function".equals(entry.path("type").asText())) {
          continue;
        }
        String mutability = entry.path("stateMutability").asText();
        if ("view".equals(mutability) || "pure".equals(mutability)) {
          readFunctions.add(entry);
        }
      }
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
      System.out.println("📜 You selected: " + functionName);

      List<Type> inputs = new ArrayList<>();
      JsonNode inputNodes = selectedFunction.path("inputs");
      if (inputNodes.isArray()) {
        for (int i = 0; i < inputNodes.size(); i++) {
          JsonNode input = inputNodes.get(i);
          String type = input.path("type").asText();
          String argName = input.path("name").asText();
          String label = (argName == null || argName.isBlank()) ? ("arg" + i) : argName;
          String raw = readRequiredTextPrompt("Enter " + label + " (" + type + ")", "");
          inputs.add(toAbiInputType(type, raw));
        }
      }

      List<TypeReference<?>> outputRefs = new ArrayList<>();
      JsonNode outputNodes = selectedFunction.path("outputs");
      if (outputNodes.isArray()) {
        for (JsonNode output : outputNodes) {
          outputRefs.add(outputTypeReference(output.path("type").asText()));
        }
      }

      Function function = new Function(functionName, inputs, outputRefs);
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        List<Type> results = ethCall(web3j, null, address, function);
        System.out.println();
        System.out.println("✅ Function " + functionName + " called successfully!");
        if (results.isEmpty()) {
          System.out.println("✔ 🔧 Result: (no return value)");
        } else if (results.size() == 1) {
          System.out.println("✔ 🔧 Result: " + readableTypeValue(results.get(0)));
        } else {
          for (int i = 0; i < results.size(); i++) {
            System.out.println("✔ 🔧 Result[" + i + "]: " + readableTypeValue(results.get(i)));
          }
        }
        System.out.println("🔗 View on Explorer: " + blockscoutAddressUrl(chainProfile, address));
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to call read function.", ex);
      }
    }
  }

  @Command(name = "bridge", description = "Bridge flow")
  static class BridgeCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = "--wallet", description = "Wallet name for write calls (defaults to active wallet)")
    String wallet;

    @Option(names = "--value", description = "RBTC value for payable write calls")
    BigDecimal value;

    @Option(names = "--gas-limit", description = "Gas limit override for write calls")
    BigInteger gasLimit;

    @Option(names = "--gas-price", description = "Gas price in wei override for write calls")
    BigInteger gasPrice;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    private String resolveWalletName() {
      if (wallet != null && !wallet.isBlank()) {
        return wallet;
      }
      return ctx.walletService()
          .active()
          .map(WalletMetadata::name)
          .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
    }

    private List<Type> parseFunctionInputs(JsonNode functionNode) {
      List<Type> inputs = new ArrayList<>();
      JsonNode inputNodes = functionNode.path("inputs");
      if (!inputNodes.isArray()) {
        return inputs;
      }
      for (int i = 0; i < inputNodes.size(); i++) {
        JsonNode input = inputNodes.get(i);
        String type = input.path("type").asText();
        String argName = input.path("name").asText();
        String label = (argName == null || argName.isBlank()) ? ("arg" + i) : argName;
        String raw = readRequiredTextPrompt("Enter " + label + " (" + type + ")", "");
        inputs.add(toAbiInputType(type, raw));
      }
      return inputs;
    }

    private List<TypeReference<?>> parseFunctionOutputs(JsonNode functionNode) {
      List<TypeReference<?>> outputRefs = new ArrayList<>();
      JsonNode outputNodes = functionNode.path("outputs");
      if (!outputNodes.isArray()) {
        return outputRefs;
      }
      for (JsonNode output : outputNodes) {
        outputRefs.add(outputTypeReference(output.path("type").asText()));
      }
      return outputRefs;
    }

    private void executeRead(
        Web3j web3j,
        String contractAddress,
        JsonNode functionNode)
        throws Exception {
      String functionName = functionNode.path("name").asText();
      List<Type> inputs = parseFunctionInputs(functionNode);
      List<TypeReference<?>> outputRefs;
      try {
        outputRefs = parseFunctionOutputs(functionNode);
      } catch (Exception ex) {
        // If output decoding type is unsupported, still perform eth_call and print raw result.
        outputRefs = List.of();
      }

      Function function = new Function(functionName, inputs, outputRefs);
      String encoded = FunctionEncoder.encode(function);
      var response =
          web3j.ethCall(
                  Transaction.createEthCallTransaction(null, contractAddress, encoded),
                  DefaultBlockParameterName.LATEST)
              .send();
      if (response.hasError()) {
        throw new IllegalStateException(response.getError().getMessage());
      }
      if (outputRefs.isEmpty()) {
        System.out.println("✅ Function " + functionName + " called successfully!");
        System.out.println("✔ 🔧 Result (raw): " + response.getValue());
        return;
      }
      @SuppressWarnings("unchecked")
      List<TypeReference<Type>> typedOutputRefs = (List<TypeReference<Type>>) (List<?>) outputRefs;
      List<Type> results = FunctionReturnDecoder.decode(response.getValue(), typedOutputRefs);
      System.out.println("✅ Function " + functionName + " called successfully!");
      if (results.isEmpty()) {
        System.out.println("✔ 🔧 Result: (no return value)");
      } else if (results.size() == 1) {
        System.out.println("✔ 🔧 Result: " + readableTypeValue(results.get(0)));
      } else {
        for (int i = 0; i < results.size(); i++) {
          System.out.println("✔ 🔧 Result[" + i + "]: " + readableTypeValue(results.get(i)));
        }
      }
    }

    private void executeWrite(
        ChainProfile chainProfile,
        Web3j web3j,
        String contractAddress,
        JsonNode functionNode)
        throws Exception {
      String functionName = functionNode.path("name").asText();
      String walletName = resolveWalletName();
      char[] password = readPassword("? Enter your password to decrypt the wallet: ");
      String privateKeyHex = ctx.walletService().dumpPrivateKey(walletName, password);
      Credentials credentials = Credentials.create(privateKeyHex);
      System.out.println("🔑 Wallet account: " + credentials.getAddress());

      List<Type> inputs = parseFunctionInputs(functionNode);
      Function function = new Function(functionName, inputs, List.of());
      String dataHex = FunctionEncoder.encode(function);

      BigInteger txValue = value == null ? BigInteger.ZERO : decimalToUnits(value, 18);
      EthGetTransactionCount nonceResponse =
          web3j
              .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
              .send();
      BigInteger nonce = nonceResponse.getTransactionCount();

      BigInteger txGasPrice = gasPrice != null ? gasPrice : web3j.ethGasPrice().send().getGasPrice();
      BigInteger txGasLimit;
      if (gasLimit != null) {
        txGasLimit = gasLimit;
      } else {
        EthEstimateGas estimate =
            web3j
                .ethEstimateGas(
                    Transaction.createFunctionCallTransaction(
                        credentials.getAddress(),
                        nonce,
                        txGasPrice,
                        null,
                        contractAddress,
                        txValue,
                        dataHex))
                .send();
        if (estimate.hasError() || estimate.getAmountUsed() == null) {
          txGasLimit = BigInteger.valueOf(300_000L);
        } else {
          txGasLimit = estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
        }
      }

      RawTransaction tx =
          RawTransaction.createTransaction(
              nonce,
              txGasPrice,
              txGasLimit,
              contractAddress,
              txValue,
              dataHex);
      byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
      EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
      if (sent.hasError()) {
        throw new IllegalStateException(sent.getError().getMessage());
      }
      String txHash = sent.getTransactionHash();
      System.out.println("🔄 Transaction initiated. TxHash: " + txHash);
      TransactionReceipt receipt = waitForReceipt(web3j, txHash, 120, 2000L);
      if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
        throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
      }
      System.out.println("✅ Transaction confirmed successfully!");
      System.out.println("📦 Block Number: " + receipt.getBlockNumber());
      System.out.println("⛽ Gas Used: " + receipt.getGasUsed());
      System.out.println("🔗 View on Explorer: " + explorerTxUrl(chainProfile, txHash));
    }

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      System.out.printf("🔧 Initializing bridge interaction on %s...%n", chainProfile.name());
      String contractAddress = RSK_BRIDGE_CONTRACT;
      JsonNode abiArray = readAbiArrayResource(BRIDGE_ABI_RESOURCE);

      List<JsonNode> functions = new ArrayList<>();
      for (JsonNode entry : abiArray) {
        if ("function".equals(entry.path("type").asText())) {
          functions.add(entry);
        }
      }
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
      System.out.println("📜 You selected: " + functionName + " [" + mutability + "]");

      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        boolean readOnly = "view".equals(mutability) || "pure".equals(mutability);
        if (readOnly) {
          executeRead(web3j, contractAddress, selected);
        } else {
          executeWrite(chainProfile, web3j, contractAddress, selected);
        }
        System.out.println("🔗 View on Explorer: " + blockscoutAddressUrl(chainProfile, contractAddress));
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Bridge interaction failed.", ex);
      }
    }
  }

  @Command(name = "history", description = "History API")
  static class HistoryCommand extends HelpCommand implements Callable<Integer> {
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

    @Option(names = "--category", description = "Comma-separated categories, e.g. erc721,erc1155")
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

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      CliConfig config = ctx.configPort().load();
      ensureConfigShape(config);

      String resolvedApiKey = apiKey;
      if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
        resolvedApiKey = config.getApiKeys().getAlchemyApiKey();
      }
      if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
        throw new IllegalArgumentException(
            "Alchemy API key is required. Provide --apikey or set config.apiKeys.alchemyApiKey.");
      }

      String maxCountHex = normalizeHexCount(maxCount != null ? maxCount : number);
      JsonNode body =
          alchemyAssetTransfersRequest(
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
      String url = resolveAlchemyUrl(chainProfile, resolvedApiKey);
      JsonNode response = postJson(url, body);
      try {
        System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response));
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to print Alchemy response.", ex);
      }
      return 0;
    }
  }

  @Command(
      name = "batch-transfer", description = "Batch transfer")
  static class BatchTransferCommand extends HelpCommand implements Callable<Integer> {
    @Option(names = {"-i", "--interactive"})
    boolean interactive;

    @Option(names = "--file")
    String file;

    @Option(names = "--rns")
    boolean rns;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    static class BatchItem {
      final String to;
      final BigDecimal value;

      BatchItem(String to, BigDecimal value) {
        this.to = to;
        this.value = value;
      }
    }

    private List<BatchItem> readInteractiveItems() {
      List<BatchItem> items = new ArrayList<>();
      System.out.println(cEmph("📦 Interactive Batch Transfer"));
      System.out.println(cWarn("Enter each recipient as address or RNS."));
      while (true) {
        String to = readRequiredTextPrompt(cInfo("Enter address or RNS"), "");
        BigDecimal amount;
        while (true) {
          String rawAmount = readRequiredTextPrompt(cInfo("Enter amount (RBTC)"), "");
          try {
            amount = new BigDecimal(rawAmount);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
              System.out.println("Amount must be greater than zero.");
              continue;
            }
            break;
          } catch (NumberFormatException ex) {
            System.out.println("Please enter a valid decimal amount.");
          }
        }
        items.add(new BatchItem(to, amount));

        String addMore = readTextPrompt(cInfo("Add another transaction? (y/n)"), "n");
        if (!"y".equalsIgnoreCase(addMore) && !"yes".equalsIgnoreCase(addMore)) {
          break;
        }
      }
      return items;
    }

    private List<BatchItem> readFileItems() {
      if (file == null || file.isBlank()) {
        return List.of();
      }
      try {
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(Path.of(file)));
        if (!root.isArray()) {
          throw new IllegalArgumentException("Batch file must be a JSON array.");
        }
        List<BatchItem> items = new ArrayList<>();
        for (JsonNode node : root) {
          String to = node.path("to").asText();
          if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Each batch item must include a non-empty 'to' field.");
          }
          JsonNode valueNode = node.path("value");
          if (valueNode.isMissingNode() || valueNode.isNull()) {
            throw new IllegalArgumentException("Each batch item must include a 'value' field.");
          }
          BigDecimal amount = new BigDecimal(valueNode.asText());
          if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Batch value must be greater than zero.");
          }
          items.add(new BatchItem(to, amount));
        }
        return items;
      } catch (IOException ex) {
        throw new IllegalArgumentException("Unable to read batch file: " + file, ex);
      }
    }

    @Override
    public Integer call() {
      if (!interactive && (file == null || file.isBlank())) {
        throw new IllegalArgumentException("Provide --interactive or --file.");
      }
      if (interactive && file != null && !file.isBlank()) {
        throw new IllegalArgumentException("Use either --interactive or --file, not both.");
      }

      ChainProfile chainProfile = resolveChain(networkOptions);
      String walletName =
          ctx.walletService()
              .active()
              .map(WalletMetadata::name)
              .orElseThrow(() -> new IllegalArgumentException("No active wallet found."));
      char[] password = readPassword("✔ Enter your password to decrypt the wallet: ");
      String privateKeyHex = ctx.walletService().dumpPrivateKey(walletName, password);
      Credentials credentials = Credentials.create(privateKeyHex);

      List<BatchItem> rawItems = interactive ? readInteractiveItems() : readFileItems();
      if (rawItems.isEmpty()) {
        throw new IllegalArgumentException("No batch transactions provided.");
      }

      List<BatchItem> resolvedItems = new ArrayList<>();
      for (BatchItem item : rawItems) {
        String resolvedTo = resolveAddressInput(chainProfile, item.to);
        resolvedItems.add(new BatchItem(resolvedTo, item.value));
      }

      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        BigInteger balanceWei =
            web3j
                .ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();
        BigDecimal balance = unitsToDecimal(balanceWei, 18);
        System.out.println("📄 Wallet Address: " + credentials.getAddress());
        System.out.println("💰 Current Balance: " + balance.toPlainString() + " " + chainProfile.nativeSymbol());

        BigInteger nonce =
            web3j
                .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                .send()
                .getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(21_000L);

        for (BatchItem item : resolvedItems) {
          BigInteger valueWei = decimalToUnits(item.value, 18);
          RawTransaction tx =
              RawTransaction.createTransaction(
                  nonce,
                  gasPrice,
                  gasLimit,
                  item.to,
                  valueWei,
                  "");
          byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
          EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
          if (sent.hasError()) {
            throw new IllegalStateException(
                "Transfer to " + item.to + " failed: " + sent.getError().getMessage());
          }

          String txHash = sent.getTransactionHash();
          System.out.println("🔄 Transaction initiated. TxHash: " + txHash);
          TransactionReceipt receipt = waitForReceipt(web3j, txHash, 120, 2000L);
          if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
            throw new IllegalStateException(
                "Transfer failed for " + item.to + ". Receipt status: " + receipt.getStatus());
          }
          System.out.println("✅ Transaction confirmed successfully!");
          System.out.println("📦 Block Number: " + receipt.getBlockNumber());
          System.out.println("⛽ Gas Used: " + receipt.getGasUsed());
          nonce = nonce.add(BigInteger.ONE);
        }
        return 0;
      } catch (Exception ex) {
        throw new IllegalStateException("Batch transfer failed.", ex);
      }
    }
  }

  @Command(
      name = "transaction",
      description = "Transaction builder")
  static class TransactionCommand extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Transaction builder placeholder.");
      return 0;
    }
  }

  @Command(name = "simulate", description = "Simulation builder")
  static class SimulateCommand extends HelpCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Simulation builder placeholder.");
      return 0;
    }
  }
}
