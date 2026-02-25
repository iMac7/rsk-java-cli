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

public final class EvmCliMain {
  private EvmCliMain() {}

  public static void main(String[] args) {
    AnsiConsole.systemInstall();
    try {
      CliContext context = new CliContext(CliContext.defaultHome());
      CommandLine commandLine = new CommandLine(new EvmCliCommand(context));
      int exitCode = args.length == 0 ? runInteractive(commandLine) : commandLine.execute(args);
      System.exit(exitCode);
    } finally {
      AnsiConsole.systemUninstall();
    }
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
        System.out.println(Ansi.ansi().fgRgb(255, 183, 77).a("Command failed.").reset());
      }
    }
  }

  private static void printWelcome() {
    System.out.println(
        Ansi.ansi().fgRgb(255, 153, 51).bold().a("Welcome to EVM CLI").reset().toString());
    System.out.println(Ansi.ansi().fgRgb(255, 183, 77).a("Type a command or use --help").reset());
    System.out.println();
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("👛 wallet      Wallet management").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("⚙️  config      Config TUI").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("💰 balance     Check native balance").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🚀 transfer    Send native transfer").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🧾 tx          Transaction status").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("📡 monitor     Session monitoring").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🔎 resolve     Resolve names").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🛠️  deploy      Deploy contract").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("✅ verify      Verify contract").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("📜 contract    Contract mode").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🌉 bridge      Bridge flow").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🕘 history     History API").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("📦 batch-transfer  Batch transfer").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🧱 transaction Transaction builder").reset());
    System.out.println(Ansi.ansi().fgRgb(255, 153, 51).a("🧪 simulate    Simulation builder").reset());
    System.out.println();
    System.out.println(
        Ansi.ansi()
            .fgRgb(255, 183, 77)
            .a("Tips: type 'clear' to redraw, 'exit' to quit.")
            .reset());
  }

  private static String prompt() {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a("🟠 evm-cli> ").reset().toString();
  }
}
