package com.rsk.utils;

import java.io.Console;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

public final class CliInput {
  private static final LineReader PASSWORD_READER = createPasswordReader();

  private CliInput() {}

  public static char[] readPassword(String prompt, String cancelMessage) {
    while (true) {
      try {
        Console console = System.console();
        if (console != null) {
          char[] password = console.readPassword(prompt);
          if (password == null) {
            throw new IllegalStateException(cancelMessage);
          }
          if (password.length == 0) {
            System.out.println("Password is required.");
            continue;
          }
          return password;
        }

        String password = PASSWORD_READER.readLine(prompt, '*');
        if (password == null) {
          throw new IllegalStateException(cancelMessage);
        }
        if (password.isBlank()) {
          System.out.println("Password is required.");
          continue;
        }
        return password.toCharArray();
      } catch (UserInterruptException ex) {
        throw new IllegalStateException(cancelMessage);
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          throw new IllegalStateException(cancelMessage);
        }
        throw ex;
      }
    }
  }

  private static LineReader createPasswordReader() {
    if (Terminal.interactiveTerminal() != null) {
      return LineReaderBuilder.builder().terminal(Terminal.interactiveTerminal()).build();
    }
    return LineReaderBuilder.builder().build();
  }
}
