package com.rsk.java_cli;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;

public class Cli {
  private Cli() {}

  public static void main(String[] args) {
    configureUtf8Console();
    CommandLine commandLine = CliHelpers.createCommandLine();
    int exitCode = args.length == 0 ? runInteractive(commandLine) : execute(commandLine, args);
    System.exit(exitCode);
  }

  private static void configureUtf8Console() {
    System.setProperty("file.encoding", "UTF-8");
    System.setProperty("sun.stdout.encoding", "UTF-8");
    System.setProperty("sun.stderr.encoding", "UTF-8");
    System.setProperty("stdout.encoding", "UTF-8");
    System.setProperty("stderr.encoding", "UTF-8");
    System.setProperty("org.jline.terminal.encoding", "UTF-8");
    try {
      System.setOut(new PrintStream(new FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(new FileOutputStream(java.io.FileDescriptor.err), true, StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // Keep default streams if reconfiguration is not allowed.
    }
  }

  private static int runInteractive(CommandLine commandLine) {
    WelcomeScreen.printWelcome();
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    while (true) {
      String line;
      try {
        System.out.print(WelcomeScreen.prompt());
        System.out.flush();
        line = reader.readLine();
      } catch (IOException e) {
        System.err.println("Error: Unable to read input.");
        return 1;
      }

      if (line == null) {
        System.out.println();
        return 0;
      }
      line = line.trim();

      if (line.isEmpty()) {
        continue;
      }
      if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
        return 0;
      }
      if (isWelcomeRedrawCommand(line)) {
        redrawWelcome();
        continue;
      }

      String[] words;
      try {
        words = splitArgs(line);
      } catch (Exception ex) {
        WelcomeScreen.printError(ex.getMessage());
        WelcomeScreen.printWelcome();
        continue;
      }
      int exitCode = execute(commandLine, words);
      if (exitCode != 0) {
        WelcomeScreen.printError("Command failed.");
        WelcomeScreen.printWelcome();
      }
    }
  }

  private static int execute(CommandLine commandLine, String[] args) {
    try {
      String[] normalizedArgs = CliHelpers.enforceWalletArgStyle(args);
      return commandLine.execute(normalizedArgs);
    } catch (IllegalArgumentException ex) {
      WelcomeScreen.printError(ex.getMessage());
      return 1;
    }
  }

  private static boolean isWelcomeRedrawCommand(String line) {
    return "clear".equalsIgnoreCase(line)
        || "help".equalsIgnoreCase(line)
        || "-h".equalsIgnoreCase(line)
        || "--help".equalsIgnoreCase(line);
  }

  private static void redrawWelcome() {
    System.out.print("\u001b[H\u001b[2J");
    System.out.flush();
    WelcomeScreen.printWelcome();
  }

  static String[] splitArgs(String line) {
    List<String> args = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (Character.isWhitespace(c) && !inQuotes) {
        if (current.length() > 0) {
          args.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(c);
    }

    if (inQuotes) {
      throw new IllegalArgumentException("Unclosed quote in command.");
    }
    if (current.length() > 0) {
      args.add(current.toString());
    }
    return args.toArray(String[]::new);
  }
}
