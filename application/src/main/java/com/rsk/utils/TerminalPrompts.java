package com.rsk.utils;

import java.io.Console;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.Supplier;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

final class TerminalPrompts {
  private TerminalPrompts() {}

  static char[] readPassword(String prompt, String cancelMessage) {
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
        if (TerminalSession.passwordReader() == null) {
          throw new IllegalStateException("Secure password entry requires a real terminal.");
        }

        String password = TerminalSession.passwordReader().readLine(prompt, '*');
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

  static char[] readPasswordWithStatus(String prompt, String cancelMessage) {
    return readPassword(TerminalText.cOk("\u2714" + prompt), cancelMessage);
  }

  static void clearPassword(char[] password) {
    if (password != null) {
      Arrays.fill(password, '\0');
    }
  }

  static <T, E extends Exception> T withClearedPassword(
      char[] password, Terminal.PasswordAction<T, E> action) throws E {
    try {
      return action.run(password);
    } finally {
      clearPassword(password);
    }
  }

  static <T, E extends Exception> T withPassword(
      String prompt, String cancelMessage, Terminal.PasswordAction<T, E> action) throws E {
    return withClearedPassword(readPassword(prompt, cancelMessage), action);
  }

  static <T, E extends Exception> T withPasswordWithStatus(
      String prompt, String cancelMessage, Terminal.PasswordAction<T, E> action) throws E {
    return withClearedPassword(readPasswordWithStatus(prompt, cancelMessage), action);
  }

  static <X extends RuntimeException> char[] readPasswordOrThrow(
      String prompt, String cancelMessage, Supplier<X> cancelledExceptionFactory) {
    try {
      return readPassword(prompt, cancelMessage);
    } catch (IllegalStateException ex) {
      if (cancelMessage.equals(ex.getMessage())) {
        throw cancelledExceptionFactory.get();
      }
      throw ex;
    }
  }

  static <X extends RuntimeException> char[] readPasswordWithStatusOrThrow(
      String prompt, String cancelMessage, Supplier<X> cancelledExceptionFactory) {
    return readPasswordOrThrow(
        TerminalText.cOk("\u2714" + prompt), cancelMessage, cancelledExceptionFactory);
  }

  static <T, E extends Exception, X extends RuntimeException> T withPasswordOrThrow(
      String prompt,
      String cancelMessage,
      Supplier<X> cancelledExceptionFactory,
      Terminal.PasswordAction<T, E> action)
      throws E {
    return withClearedPassword(
        readPasswordOrThrow(prompt, cancelMessage, cancelledExceptionFactory), action);
  }

  static <T, E extends Exception, X extends RuntimeException> T withPasswordWithStatusOrThrow(
      String prompt,
      String cancelMessage,
      Supplier<X> cancelledExceptionFactory,
      Terminal.PasswordAction<T, E> action)
      throws E {
    return withClearedPassword(
        readPasswordWithStatusOrThrow(prompt, cancelMessage, cancelledExceptionFactory), action);
  }

  static <X extends RuntimeException> String promptRequiredText(
      LineReader reader,
      String prompt,
      String requiredMessage,
      Supplier<X> cancelledExceptionFactory) {
    while (true) {
      try {
        String value = reader.readLine(prompt);
        if (value != null && !value.isBlank()) {
          return value.trim();
        }
        System.out.println(TerminalText.cError(requiredMessage));
      } catch (UserInterruptException ex) {
        throw cancelledExceptionFactory.get();
      }
    }
  }

  static <X extends RuntimeException> String promptOptionalText(
      LineReader reader, String prompt, Supplier<X> cancelledExceptionFactory) {
    try {
      String value = reader.readLine(prompt);
      return value == null ? "" : value.trim();
    } catch (UserInterruptException ex) {
      throw cancelledExceptionFactory.get();
    }
  }

  static <X extends RuntimeException> BigDecimal promptPositiveAmount(
      LineReader reader,
      String prompt,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    while (true) {
      try {
        String value = reader.readLine(prompt);
        BigDecimal amount = new BigDecimal(value.trim());
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
          return amount;
        }
      } catch (UserInterruptException ex) {
        throw cancelledExceptionFactory.get();
      } catch (Exception ignored) {
      }
      System.out.println(TerminalText.cError(invalidMessage));
    }
  }

  static <X extends RuntimeException> BigInteger promptOptionalInteger(
      LineReader reader,
      String prompt,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    while (true) {
      try {
        String value = reader.readLine(prompt);
        if (value == null || value.isBlank()) {
          return null;
        }
        return new BigInteger(value.trim());
      } catch (UserInterruptException ex) {
        throw cancelledExceptionFactory.get();
      } catch (Exception ignored) {
        System.out.println(TerminalText.cError(invalidMessage));
      }
    }
  }

  static <X extends RuntimeException> BigDecimal promptOptionalDecimal(
      LineReader reader,
      String prompt,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    while (true) {
      try {
        String value = reader.readLine(prompt);
        if (value == null || value.isBlank()) {
          return null;
        }
        return new BigDecimal(value.trim());
      } catch (UserInterruptException ex) {
        throw cancelledExceptionFactory.get();
      } catch (Exception ignored) {
        System.out.println(TerminalText.cError(invalidMessage));
      }
    }
  }

  static <X extends RuntimeException> boolean promptYesNo(
      LineReader reader,
      String prompt,
      boolean defaultValue,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    while (true) {
      try {
        String raw = reader.readLine(prompt);
        if (raw == null || raw.isBlank()) {
          return defaultValue;
        }
        if ("y".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)) {
          return true;
        }
        if ("n".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw)) {
          return false;
        }
      } catch (UserInterruptException ex) {
        throw cancelledExceptionFactory.get();
      }
      System.out.println(TerminalText.cError(invalidMessage));
    }
  }

  static String rootMessage(Throwable ex) {
    Throwable current = ex;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? ex.getMessage() : current.getMessage();
  }
}
