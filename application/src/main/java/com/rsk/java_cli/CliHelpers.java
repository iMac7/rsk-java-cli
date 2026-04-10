package com.rsk.java_cli;

import com.rsk.commands.config.Subcommands.ConfigCommand;
import com.rsk.commands.balance.Subcommands.BalanceCommand;
import com.rsk.commands.batchtransfer.Subcommands.BatchTransferCommand;
import com.rsk.commands.bridge.Subcommands.BridgeCommand;
import com.rsk.commands.contract.Subcommands.ContractCommand;
import com.rsk.commands.deploy.Subcommands.DeployCommand;
import com.rsk.commands.history.Subcommands.HistoryCommand;
import com.rsk.commands.resolve.Subcommands.ResolveCommand;
import com.rsk.commands.simulate.Subcommands.SimulateCommand;
import com.rsk.commands.transfer.Subcommands.TransferCommand;
import com.rsk.commands.transaction.Subcommands.TransactionCommand;
import com.rsk.commands.tx.Subcommands.TxCommand;
import com.rsk.commands.verify.Subcommands.VerifyCommand;
import com.rsk.commands.wallet.Subcommands.WalletCommand;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

public class CliHelpers {
  private static final String RESET = "\u001b[0m";
  private static final String SOFT_ORANGE = "\u001b[38;2;255;183;77m";
  private static final String BOLD = "\u001b[1m";

  private CliHelpers() {}

  public static CommandLine createCommandLine() {
    CommandLine commandLine = new CommandLine(new RootCommand());
    removeSubcommandVersionOptions(commandLine);
    commandLine.setSeparator(" ");
    commandLine.setColorScheme(createHelpColorScheme());
    applyHelpFormatting(commandLine);
    commandLine.setParameterExceptionHandler(createParameterExceptionHandler());
    commandLine.setExecutionExceptionHandler(
        (ex, cmd, parseResult) -> {
          String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
          WelcomeScreen.printError(message);
          return cmd.getCommandSpec().exitCodeOnExecutionException();
        });
    return commandLine;
  }

  public static LazyDependencies deps(CommandSpec spec) {
    return ((RootCommand) spec.root().userObject()).deps;
  }

  private static void removeSubcommandVersionOptions(CommandLine root) {
    root.getSubcommands().values().forEach(CliHelpers::removeVersionOptionsRecursively);
  }

  private static void removeVersionOptionsRecursively(CommandLine commandLine) {
    CommandSpec spec = commandLine.getCommandSpec();
    spec.options().stream()
        .filter(OptionSpec::versionHelp)
        .toList()
        .forEach(spec::remove);
    commandLine.getSubcommands().values().forEach(CliHelpers::removeVersionOptionsRecursively);
  }

  public static String[] enforceWalletArgStyle(String[] args) {
    for (String arg : args) {
      if (arg != null && arg.startsWith("--wallet=")) {
        throw new IllegalArgumentException("Use --wallet <name> instead of --wallet=<name>.");
      }
    }
    return args;
  }

  private static Help.ColorScheme createHelpColorScheme() {
    return new Help.ColorScheme.Builder(Help.Ansi.AUTO)
        .commands(Help.Ansi.Style.bold, Help.Ansi.Style.fg("214"))
        .options(Help.Ansi.Style.bold, Help.Ansi.Style.fg("208"))
        .parameters(Help.Ansi.Style.bold, Help.Ansi.Style.fg("208"))
        .optionParams(Help.Ansi.Style.bold, Help.Ansi.Style.fg("208"))
        .errors(Help.Ansi.Style.bold, Help.Ansi.Style.fg_red)
        .build();
  }

  private static void applyHelpFormatting(CommandLine commandLine) {
    CommandSpec spec = commandLine.getCommandSpec();
    spec.usageMessage().synopsisHeading(BOLD + SOFT_ORANGE + "Usage:%n" + RESET);
    spec.usageMessage().descriptionHeading("%n" + BOLD + SOFT_ORANGE + "Description:%n" + RESET);
    spec.usageMessage().optionListHeading("%n" + BOLD + SOFT_ORANGE + "Options:%n" + RESET);
    spec.usageMessage().parameterListHeading("%n" + BOLD + SOFT_ORANGE + "Arguments:%n" + RESET);
    spec.usageMessage().commandListHeading("%n" + BOLD + SOFT_ORANGE + "Commands:%n" + RESET);
    commandLine.getSubcommands().values().forEach(CliHelpers::applyHelpFormatting);
  }

  private static IParameterExceptionHandler createParameterExceptionHandler() {
    return (ParameterException ex, String[] args) -> {
      WelcomeScreen.printError(ex.getMessage());
      return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
    };
  }

  @Command(
      name = "rsk-java-cli",
      description = "RSK Java CLI",
      mixinStandardHelpOptions = true,
      subcommands = {
        WalletCommand.class,
        ConfigCommand.class,
        BalanceCommand.class,
        BatchTransferCommand.class,
        BridgeCommand.class,
        ContractCommand.class,
        DeployCommand.class,
        HistoryCommand.class,
        ResolveCommand.class,
        SimulateCommand.class,
        TransferCommand.class,
        TransactionCommand.class,
        TxCommand.class,
        VerifyCommand.class
      })
  static class RootCommand implements Callable<Integer> {
    final LazyDependencies deps = new LazyDependencies();

    @Spec CommandSpec spec;

    @Override
    public Integer call() {
      spec.commandLine().usage(spec.commandLine().getOut());
      return 0;
    }
  }

  public static final class LazyDependencies {
    private com.rsk.commands.wallet.Helpers walletHelpers;
    private com.rsk.commands.config.Helpers configHelpers;
    private com.rsk.commands.balance.Helpers balanceHelpers;
    private com.rsk.commands.batchtransfer.Helpers batchTransferHelpers;
    private com.rsk.commands.bridge.Helpers bridgeHelpers;
    private com.rsk.commands.contract.Helpers contractHelpers;
    private com.rsk.commands.deploy.Helpers deployHelpers;
    private com.rsk.commands.history.Helpers historyHelpers;
    private com.rsk.commands.resolve.Helpers resolveHelpers;
    private com.rsk.commands.simulate.Helpers simulateHelpers;
    private com.rsk.commands.transfer.Helpers transferHelpers;
    private com.rsk.commands.tx.Helpers txHelpers;
    private com.rsk.commands.verify.Helpers verifyHelpers;

    public com.rsk.commands.wallet.Helpers walletHelpers() {
      if (walletHelpers == null) {
        walletHelpers = com.rsk.commands.wallet.Helpers.defaultHelpers();
      }
      return walletHelpers;
    }

    public com.rsk.commands.config.Helpers configHelpers() {
      if (configHelpers == null) {
        configHelpers = com.rsk.commands.config.Helpers.defaultHelpers();
      }
      return configHelpers;
    }

    public com.rsk.commands.balance.Helpers balanceHelpers() {
      if (balanceHelpers == null) {
        balanceHelpers = com.rsk.commands.balance.Helpers.defaultHelpers();
      }
      return balanceHelpers;
    }

    public com.rsk.commands.batchtransfer.Helpers batchTransferHelpers() {
      if (batchTransferHelpers == null) {
        batchTransferHelpers = com.rsk.commands.batchtransfer.Helpers.defaultHelpers();
      }
      return batchTransferHelpers;
    }

    public com.rsk.commands.bridge.Helpers bridgeHelpers() {
      if (bridgeHelpers == null) {
        bridgeHelpers = com.rsk.commands.bridge.Helpers.defaultHelpers();
      }
      return bridgeHelpers;
    }

    public com.rsk.commands.contract.Helpers contractHelpers() {
      if (contractHelpers == null) {
        contractHelpers = com.rsk.commands.contract.Helpers.defaultHelpers();
      }
      return contractHelpers;
    }

    public com.rsk.commands.deploy.Helpers deployHelpers() {
      if (deployHelpers == null) {
        deployHelpers = com.rsk.commands.deploy.Helpers.defaultHelpers();
      }
      return deployHelpers;
    }

    public com.rsk.commands.history.Helpers historyHelpers() {
      if (historyHelpers == null) {
        historyHelpers = com.rsk.commands.history.Helpers.defaultHelpers();
      }
      return historyHelpers;
    }

    public com.rsk.commands.resolve.Helpers resolveHelpers() {
      if (resolveHelpers == null) {
        resolveHelpers = com.rsk.commands.resolve.Helpers.defaultHelpers();
      }
      return resolveHelpers;
    }

    public com.rsk.commands.simulate.Helpers simulateHelpers() {
      if (simulateHelpers == null) {
        simulateHelpers = com.rsk.commands.simulate.Helpers.defaultHelpers();
      }
      return simulateHelpers;
    }

    public com.rsk.commands.transfer.Helpers transferHelpers() {
      if (transferHelpers == null) {
        transferHelpers = com.rsk.commands.transfer.Helpers.defaultHelpers();
      }
      return transferHelpers;
    }

    public com.rsk.commands.tx.Helpers txHelpers() {
      if (txHelpers == null) {
        txHelpers = com.rsk.commands.tx.Helpers.defaultHelpers();
      }
      return txHelpers;
    }

    public com.rsk.commands.verify.Helpers verifyHelpers() {
      if (verifyHelpers == null) {
        verifyHelpers = com.rsk.commands.verify.Helpers.defaultHelpers();
      }
      return verifyHelpers;
    }
  }
}
