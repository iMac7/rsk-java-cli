package com.evmcli.cli;

import java.util.List;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import picocli.CommandLine;
import picocli.CommandLine.Help;

public final class EvmCliMain {
  private EvmCliMain() {}

  public static void main(String[] args) {
    AnsiConsole.systemInstall();
    try {
      CliContext context = new CliContext(CliContext.defaultHome());
      CommandLine commandLine = new CommandLine(new EvmCliCommand(context));
      commandLine.setColorScheme(createHelpColorScheme());
      commandLine.setExecutionExceptionHandler(
          (ex, cmd, parseResult) -> {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            cmd.getErr()
                .println(Ansi.ansi().fg(Ansi.Color.RED).bold().a("Error: ").a(message).reset());
            cmd.getErr().println();
            CommandLine.ParseResult target = parseResult;
            while (target.hasSubcommand()) {
              target = target.subcommand();
            }
            target.commandSpec().commandLine().usage(cmd.getErr());
            return cmd.getCommandSpec().exitCodeOnExecutionException();
          });
      int exitCode = args.length == 0 ? runInteractive(commandLine) : commandLine.execute(args);
      System.exit(exitCode);
    } finally {
      AnsiConsole.systemUninstall();
    }
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

  private static int runInteractive(CommandLine commandLine) {
    printWelcome();
    LineReader reader = LineReaderBuilder.builder().build();
    DefaultParser parser = new DefaultParser();

    while (true) {
      String line;
      try {
        line = reader.readLine(prompt()).trim();
      } catch (UserInterruptException e) {
        System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a("Input cancelled. Please try again.").reset());
        continue;
      } catch (EndOfFileException e) {
        System.out.println();
        return 0;
      }

      if (line.isEmpty()) {
        continue;
      }
      if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
        return 0;
      }
      if ("clear".equalsIgnoreCase(line)) {
        System.out.print("\u001b[H\u001b[2J");
        System.out.flush();
        printWelcome();
        continue;
      }

      List<String> words = parser.parse(line, 0).words();
      int exitCode = commandLine.execute(words.toArray(String[]::new));
      if (exitCode != 0) {
        System.out.println(Ansi.ansi().fg(Ansi.Color.RED).bold().a("Command failed.").reset());
      }
    }
  }

  private static void printWelcome() {
    System.out.println(
        Ansi.ansi().fgRgb(255, 153, 51).bold().a("Welcome to EVM CLI").reset().toString());
    System.out.println(Ansi.ansi().fgRgb(255, 183, 77).a("Type a command or use --help").reset());
    System.out.println();
    printMenuItem("\uD83D\uDC5B wallet      ", "Wallet management");
    printMenuItem("\u2699\uFE0F  config      ", "Config UI");
    printMenuItem("\uD83D\uDCB0 balance     ", "Check native balance");
    printMenuItem("\uD83D\uDE80 transfer    ", "Send native transfer");
    printMenuItem("\uD83E\uDDFE tx          ", "Transaction status");
    printMenuItem("\uD83D\uDCE1 monitor     ", "Session monitoring");
    printMenuItem("\uD83D\uDD0E resolve     ", "Resolve names");
    printMenuItem("\uD83D\uDEE0\uFE0F  deploy      ", "Deploy contract");
    printMenuItem("\u2705 verify      ", "Verify contract");
    printMenuItem("\uD83D\uDCDC contract    ", "Contract mode");
    printMenuItem("\uD83C\uDF09 bridge      ", "Bridge flow");
    printMenuItem("\uD83D\uDD58 history     ", "History API");
    printMenuItem("\uD83D\uDCE6 batch-transfer  ", "Batch transfer");
    printMenuItem("\uD83E\uDDF1 transaction ", "Transaction builder");
    printMenuItem("\uD83E\uDDEA simulate    ", "Simulation builder");
    System.out.println();
    System.out.println(
        Ansi.ansi()
            .fgRgb(255, 183, 77)
            .a("Type 'clear' to start over, 'exit' to quit.")
            .reset());
  }

  private static String prompt() {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a("\uD83D\uDFE0 evm-cli> ").reset().toString();
  }

  private static void printMenuItem(String key, String description) {
    System.out.println(
        Ansi.ansi()
            .fgRgb(255, 153, 51)
            .bold()
            .a(key)
            .reset()
            .fg(Ansi.Color.WHITE)
            .a(description)
            .reset());
  }
}
