package com.rsk.java_cli;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import picocli.CommandLine;

public class Cli {
  private Cli() {}

  public static void main(String[] args) {
    configureLogging();
    configureUtf8Console();
    CommandLine commandLine = CliHelpers.createCommandLine();
    int exitCode = args.length == 0 ? runInteractive(commandLine) : execute(commandLine, args);
    System.exit(exitCode);
  }

  private static void configureLogging() {
    Path cliHome = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    try {
      Files.createDirectories(cliHome);
    } catch (IOException ignored) {
      // Keep startup resilient. Logback will fall back to its own error reporting if needed.
    }

    Properties cliConfig = loadCliConfig(cliHome.resolve("cli_config.txt"));
    String logFile = resolveLoggingSetting("rsk.cli.log.file", cliConfig, "rskCliLogFile", cliHome.resolve("logs.txt").toString());
    String rootLevel = resolveLoggingSetting("rsk.cli.log.root.level", cliConfig, "rskCliLogRootLevel", "INFO");
    String appLevel = resolveLoggingSetting("rsk.cli.log.app.level", cliConfig, "rskCliLogAppLevel", "DEBUG");
    String consoleLevel = resolveLoggingSetting("rsk.cli.log.console.level", cliConfig, "rskCliLogConsoleLevel", "OFF");

    System.setProperty("rsk.cli.log.file", logFile);
    System.setProperty("rsk.cli.log.root.level", rootLevel);
    System.setProperty("rsk.cli.log.app.level", appLevel);
    System.setProperty("rsk.cli.log.console.level", consoleLevel);
  }

  private static Properties loadCliConfig(Path configPath) {
    Properties properties = new Properties();
    if (!Files.exists(configPath)) {
      return properties;
    }

    try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException ignored) {
      // Keep startup resilient. Logging falls back to defaults if the config cannot be read.
    }
    return properties;
  }

  private static String resolveLoggingSetting(
      String systemPropertyKey, Properties cliConfig, String cliConfigKey, String defaultValue) {
    String systemPropertyValue = trimToNull(System.getProperty(systemPropertyKey));
    if (systemPropertyValue != null) {
      return systemPropertyValue;
    }

    String cliConfigValue = trimToNull(cliConfig.getProperty(cliConfigKey));
    if (cliConfigValue != null) {
      return cliConfigValue;
    }

    return defaultValue;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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
    Character quoteDelimiter = null;
    boolean escaping = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);

      if (escaping) {
        current.append(c);
        escaping = false;
        continue;
      }

      if (c == '\\') {
        if (i + 1 < line.length() && isEscapable(line.charAt(i + 1))) {
          escaping = true;
          continue;
        }
        current.append(c);
        continue;
      }

      if ((c == '"' || c == '\'') && (quoteDelimiter == null || quoteDelimiter == c)) {
        quoteDelimiter = quoteDelimiter == null ? c : null;
        continue;
      }

      if (Character.isWhitespace(c) && quoteDelimiter == null) {
        if (current.length() > 0) {
          args.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(c);
    }

    if (escaping) {
      current.append('\\');
    }
    if (quoteDelimiter != null) {
      throw new IllegalArgumentException("Unclosed quote in command.");
    }
    if (current.length() > 0) {
      args.add(current.toString());
    }
    return args.toArray(String[]::new);
  }

  private static boolean isEscapable(char c) {
    return Character.isWhitespace(c) || c == '"' || c == '\'' || c == '\\';
  }
}
