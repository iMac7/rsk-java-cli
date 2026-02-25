package com.evmcli.cli;

import com.evmcli.application.ChainSelection;
import com.evmcli.application.ChainSelector;
import com.evmcli.domain.model.ChainFeatures;
import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.model.CliConfig;
import com.evmcli.domain.model.MonitorSession;
import com.evmcli.domain.model.WalletMetadata;
import java.io.Console;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "evm-cli",
    mixinStandardHelpOptions = true,
    description = "EVM multi-chain CLI",
    subcommands = {
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
  private static CliContext ctx;

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

  static ChainProfile resolveChain(NetworkOptions options) {
    return ChainSelector.resolve(
        ctx.configPort().load(),
        new ChainSelection(
            options.selector.mainnet, options.selector.testnet, options.selector.chain));
  }

  static char[] readPassword(String prompt) {
    Console console = System.console();
    if (console != null) {
      return console.readPassword(prompt);
    }
    String password = LineReaderBuilder.builder().build().readLine(prompt, '*');
    return password.toCharArray();
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
  }

  static String promptText(LineReader reader, String label, String defaultValue) {
    String prompt =
        defaultValue == null || defaultValue.isBlank()
            ? label + ": "
            : label + " [" + defaultValue + "]: ";
    while (true) {
      try {
        String value = reader.readLine(prompt);
        if ((value == null || value.isBlank()) && defaultValue != null) {
          return defaultValue;
        }
        return value == null ? "" : value.trim();
      } catch (UserInterruptException ignored) {
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
    System.out.println("=== Config TUI ===");
    if (dirty) {
      System.out.println("* Unsaved changes");
    }
    System.out.println(
        "Wallet cache password in memory: "
            + (config.getWallet().isCachePasswordInMemory() ? "enabled" : "disabled"));
    printChainSlot("mainnet", config.getChains().getMainnet());
    printChainSlot("testnet", config.getChains().getTestnet());
    System.out.println("Custom chains:");
    if (config.getChains().getCustom().isEmpty()) {
      System.out.println("- (none)");
    } else {
      for (Map.Entry<String, ChainProfile> entry : config.getChains().getCustom().entrySet()) {
        ChainProfile profile = entry.getValue();
        System.out.printf("- %s -> rpc=%s chainId=%d symbol=%s%n", entry.getKey(), profile.rpcUrl(), profile.chainId(), profile.nativeSymbol());
      }
    }
    System.out.println();
    System.out.println("1) Edit mainnet");
    System.out.println("2) Edit testnet");
    System.out.println("3) Add or update custom chain");
    System.out.println("4) Remove custom chain");
    System.out.println("5) Toggle wallet password cache");
    System.out.println("6) Save and exit");
    System.out.println("7) Exit without saving");
    System.out.println();
  }

  static void printChainSlot(String label, ChainProfile profile) {
    if (profile == null) {
      System.out.println(label + ": (not set)");
      return;
    }
    System.out.printf(
        "%s: name=%s rpc=%s chainId=%d symbol=%s%n",
        label, profile.name(), profile.rpcUrl(), profile.chainId(), profile.nativeSymbol());
  }

  static class NetworkSelector {
    @Option(names = "--mainnet", description = "Use chains.mainnet")
    boolean mainnet;

    @Option(names = "--testnet", description = "Use chains.testnet")
    boolean testnet;

    @Option(names = "--chain", description = "Use chains.custom.<name>")
    String chain;
  }

  static class NetworkOptions {
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkSelector selector = new NetworkSelector();
  }

  @Command(
      name = "wallet",
      mixinStandardHelpOptions = true,
      description = "Wallet management",
      subcommands = {
        WalletCreate.class,
        WalletImport.class,
        WalletList.class,
        WalletSwitch.class,
        WalletRename.class,
        WalletDelete.class,
        WalletBackup.class,
        WalletAddressBook.class
      })
  static class WalletCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Use a wallet subcommand.");
      return 0;
    }
  }

  @Command(name = "create", mixinStandardHelpOptions = true, description = "Create wallet")
  static class WalletCreate implements Callable<Integer> {
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
      mixinStandardHelpOptions = true,
      description = "Import wallet from private key")
  static class WalletImport implements Callable<Integer> {
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

  @Command(name = "list", mixinStandardHelpOptions = true, description = "List wallets")
  static class WalletList implements Callable<Integer> {
    @Override
    public Integer call() {
      List<WalletMetadata> wallets = ctx.walletService().list();
      if (wallets.isEmpty()) {
        System.out.println("No wallets found.");
        return 0;
      }
      wallets.forEach(w -> System.out.printf("- %s %s%n", w.name(), w.address()));
      return 0;
    }
  }

  @Command(name = "switch", mixinStandardHelpOptions = true, description = "Switch active wallet")
  static class WalletSwitch implements Callable<Integer> {
    @Parameters(index = "0", description = "Wallet name")
    String name;

    @Override
    public Integer call() {
      ctx.walletService().switchActive(name);
      System.out.println("Active wallet switched to " + name);
      return 0;
    }
  }

  @Command(name = "rename", mixinStandardHelpOptions = true, description = "Rename wallet")
  static class WalletRename implements Callable<Integer> {
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

  @Command(name = "delete", mixinStandardHelpOptions = true, description = "Delete wallet")
  static class WalletDelete implements Callable<Integer> {
    @Parameters(index = "0", description = "Wallet name")
    String name;

    @Override
    public Integer call() {
      ctx.walletService().delete(name);
      System.out.println("Deleted wallet " + name);
      return 0;
    }
  }

  @Command(name = "backup", mixinStandardHelpOptions = true, description = "Backup wallet")
  static class WalletBackup implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Wallet backup TUI placeholder.");
      return 0;
    }
  }

  @Command(
      name = "address-book", mixinStandardHelpOptions = true, description = "Address book TUI")
  static class WalletAddressBook implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Address book TUI placeholder.");
      return 0;
    }
  }

  @Command(name = "config", mixinStandardHelpOptions = true, description = "Config TUI")
  static class ConfigCommand implements Callable<Integer> {
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
            ctx.configPort().save(config);
            System.out.println("Config saved.");
            return 0;
          }
          case "7" -> {
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

  @Command(name = "balance", mixinStandardHelpOptions = true, description = "Get native balance")
  static class BalanceCommand implements Callable<Integer> {
    @ArgGroup(exclusive = true, multiplicity = "1")
    Target target;

    @Option(names = "--rns", description = "Optional resolver target")
    String rns;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    static class Target {
      @Option(names = "--wallet")
      String wallet;

      @Option(names = "--address")
      String address;
    }

    @Override
    public Integer call() {
      ChainProfile chainProfile = resolveChain(networkOptions);
      String address = target.address;
      if (address == null) {
        WalletMetadata wallet =
            ctx.walletService().list().stream()
                .filter(w -> w.name().equals(target.wallet))
                .findFirst()
                .orElseThrow(
                    () -> new IllegalArgumentException("Wallet not found: " + target.wallet));
        address = wallet.address();
      }
      BigInteger wei = ctx.balanceService().nativeBalanceWei(chainProfile, address);
      System.out.printf(
          "%s balance on %s: %s wei (%s %s)%n",
          address,
          chainProfile.name(),
          wei,
          ctx.balanceService().toNative(wei).toPlainString(),
          chainProfile.nativeSymbol());
      return 0;
    }
  }

  @Command(
      name = "transfer", mixinStandardHelpOptions = true, description = "Send native transfer")
  static class TransferCommand implements Callable<Integer> {
    @Option(names = "--wallet", required = true)
    String wallet;

    @ArgGroup(exclusive = true, multiplicity = "1")
    ToTarget toTarget;

    @Option(names = "--token", description = "ERC-20 token address")
    String token;

    @Option(names = "--value", required = true, description = "Value in wei")
    BigInteger value;

    @Option(names = "--gas-limit", defaultValue = "21000")
    BigInteger gasLimit;

    @Option(names = "--gas-price", defaultValue = "50000000")
    BigInteger gasPrice;

    @Option(names = "--data", defaultValue = "")
    String data;

    @Option(names = "--interactive")
    boolean interactive;

    @picocli.CommandLine.Mixin NetworkOptions networkOptions;

    static class ToTarget {
      @Option(names = "--address")
      String address;

      @Option(names = "--rns")
      String rns;
    }

    @Override
    public Integer call() {
      if (token != null) {
        System.out.println("ERC-20 transfer builder is reserved for the next milestone.");
        return 0;
      }
      ChainProfile chainProfile = resolveChain(networkOptions);
      String to = toTarget.address != null ? toTarget.address : toTarget.rns;
      char[] password = readPassword("Wallet password: ");
      String txHash =
          ctx.transferService()
              .sendNative(chainProfile, wallet, password, to, value, gasLimit, gasPrice, data);
      System.out.println("Submitted tx: " + txHash);
      return 0;
    }
  }

  @Command(name = "tx", mixinStandardHelpOptions = true, description = "Transaction status")
  static class TxCommand implements Callable<Integer> {
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

  @Command(name = "monitor", mixinStandardHelpOptions = true, description = "Monitor sessions")
  static class MonitorCommand implements Callable<Integer> {
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

    @Override
    public Integer call() {
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
        MonitorSession session = ctx.monitorManager().startTxConfirmations("mainnet", tx, 10, confirmations);
        System.out.println("Started tx monitor session " + session.getId());
        return 0;
      }
      System.out.println("Use --list, --stop, or --tx.");
      return 0;
    }
  }

  @Command(name = "resolve", mixinStandardHelpOptions = true, description = "Resolve name")
  static class ResolveCommand implements Callable<Integer> {
    @Parameters(index = "0")
    String name;

    @Option(names = "--reverse")
    boolean reverse;

    @Override
    public Integer call() {
      System.out.println("Name resolution is chain-capability gated and pending adapter implementation.");
      return 0;
    }
  }

  @Command(name = "deploy", mixinStandardHelpOptions = true, description = "Deploy contract")
  static class DeployCommand implements Callable<Integer> {
    @Option(names = "--abi")
    String abi;

    @Option(names = "--bytecode")
    String bytecode;

    @Option(names = "--wallet")
    String wallet;

    @Option(names = "--args")
    List<String> args;

    @Override
    public Integer call() {
      System.out.println("Contract deploy flow placeholder.");
      return 0;
    }
  }

  @Command(name = "verify", mixinStandardHelpOptions = true, description = "Verify contract")
  static class VerifyCommand implements Callable<Integer> {
    @Option(names = "--json")
    String json;

    @Option(names = "--name")
    String name;

    @Option(names = "--address")
    String address;

    @Option(names = "--decodedArgs")
    List<String> decodedArgs;

    @Override
    public Integer call() {
      System.out.println("Contract verification feature placeholder.");
      return 0;
    }
  }

  @Command(
      name = "contract",
      mixinStandardHelpOptions = true,
      description = "Interactive contract mode")
  static class ContractCommand implements Callable<Integer> {
    @Option(names = "--address")
    String address;

    @Override
    public Integer call() {
      System.out.println("Interactive contract TUI placeholder.");
      return 0;
    }
  }

  @Command(name = "bridge", mixinStandardHelpOptions = true, description = "Bridge flow")
  static class BridgeCommand implements Callable<Integer> {
    @Option(names = "--wallet")
    String wallet;

    @Override
    public Integer call() {
      System.out.println("Bridge capability placeholder.");
      return 0;
    }
  }

  @Command(name = "history", mixinStandardHelpOptions = true, description = "History API")
  static class HistoryCommand implements Callable<Integer> {
    @Option(names = "--apiKey")
    String apiKey;

    @Option(names = "--number")
    Integer number;

    @Override
    public Integer call() {
      System.out.println("History capability placeholder.");
      return 0;
    }
  }

  @Command(
      name = "batch-transfer", mixinStandardHelpOptions = true, description = "Batch transfer")
  static class BatchTransferCommand implements Callable<Integer> {
    @Option(names = "--interactive")
    boolean interactive;

    @Option(names = "--file")
    String file;

    @Option(names = "--rns")
    boolean rns;

    @Override
    public Integer call() {
      System.out.println("Batch transfer builder placeholder.");
      return 0;
    }
  }

  @Command(
      name = "transaction",
      mixinStandardHelpOptions = true,
      description = "Transaction builder")
  static class TransactionCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Transaction builder placeholder.");
      return 0;
    }
  }

  @Command(name = "simulate", mixinStandardHelpOptions = true, description = "Simulation builder")
  static class SimulateCommand implements Callable<Integer> {
    @Override
    public Integer call() {
      System.out.println("Simulation builder placeholder.");
      return 0;
    }
  }
}
