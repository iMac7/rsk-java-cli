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
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

public class CliHelpers {
  private CliHelpers() {}

  public static CommandLine createCommandLine() {
    CommandLine commandLine = new CommandLine(new RootCommand());
    commandLine.setColorScheme(createHelpColorScheme());
    commandLine.setParameterExceptionHandler(createParameterExceptionHandler());
    commandLine.setExecutionExceptionHandler(
        (ex, cmd, parseResult) -> {
          String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
          WelcomeScreen.printError(message);
          return cmd.getCommandSpec().exitCodeOnExecutionException();
        });
    return commandLine;
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
        .parameters(Help.Ansi.Style.fg("220"))
        .optionParams(Help.Ansi.Style.fg("221"))
        .errors(Help.Ansi.Style.bold, Help.Ansi.Style.fg_red)
        .build();
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
    @Spec CommandSpec spec;

    @Override
    public Integer call() {
      spec.commandLine().usage(spec.commandLine().getOut());
      return 0;
    }
  }
}
