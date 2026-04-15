package com.rsk.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;
import org.jline.reader.LineReader;
import org.jline.reader.Parser;

public final class Terminal {
  private Terminal() {}

  @FunctionalInterface
  public interface PasswordAction<T, E extends Exception> {
    T run(char[] password) throws E;
  }

  public static String pick(String unicodeValue, String asciiValue) {
    return TerminalText.pick(unicodeValue, asciiValue);
  }

  public static org.jline.terminal.Terminal interactiveTerminal() {
    return TerminalSession.interactiveTerminal();
  }

  public static org.jline.terminal.Terminal promptTerminal() {
    return TerminalSession.promptTerminal();
  }

  public static LineReader createPromptReader() {
    return createPromptReader(null);
  }

  public static LineReader createPromptReader(Parser parser) {
    return TerminalSession.createPromptReader(parser);
  }

  public static char[] readPassword(String prompt, String cancelMessage) {
    return TerminalPrompts.readPassword(prompt, cancelMessage);
  }

  public static char[] readPasswordWithStatus(String prompt, String cancelMessage) {
    return TerminalPrompts.readPasswordWithStatus(prompt, cancelMessage);
  }

  public static void clearPassword(char[] password) {
    TerminalPrompts.clearPassword(password);
  }

  public static <T, E extends Exception> T withClearedPassword(
      char[] password, PasswordAction<T, E> action) throws E {
    return TerminalPrompts.withClearedPassword(password, action);
  }

  public static <T, E extends Exception> T withPassword(
      String prompt, String cancelMessage, PasswordAction<T, E> action) throws E {
    return TerminalPrompts.withPassword(prompt, cancelMessage, action);
  }

  public static <T, E extends Exception> T withPasswordWithStatus(
      String prompt, String cancelMessage, PasswordAction<T, E> action) throws E {
    return TerminalPrompts.withPasswordWithStatus(prompt, cancelMessage, action);
  }

  public static <X extends RuntimeException> char[] readPasswordOrThrow(
      String prompt, String cancelMessage, Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.readPasswordOrThrow(prompt, cancelMessage, cancelledExceptionFactory);
  }

  public static <X extends RuntimeException> char[] readPasswordWithStatusOrThrow(
      String prompt, String cancelMessage, Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.readPasswordWithStatusOrThrow(
        prompt, cancelMessage, cancelledExceptionFactory);
  }

  public static <T, E extends Exception, X extends RuntimeException> T withPasswordOrThrow(
      String prompt,
      String cancelMessage,
      Supplier<X> cancelledExceptionFactory,
      PasswordAction<T, E> action)
      throws E {
    return TerminalPrompts.withPasswordOrThrow(
        prompt, cancelMessage, cancelledExceptionFactory, action);
  }

  public static <T, E extends Exception, X extends RuntimeException>
      T withPasswordWithStatusOrThrow(
          String prompt,
          String cancelMessage,
          Supplier<X> cancelledExceptionFactory,
          PasswordAction<T, E> action)
          throws E {
    return TerminalPrompts.withPasswordWithStatusOrThrow(
        prompt, cancelMessage, cancelledExceptionFactory, action);
  }

  public static void copyToClipboard(String value) {
    TerminalClipboard.copyToClipboard(value);
  }

  public static <X extends RuntimeException> String promptRequiredText(
      LineReader reader,
      String prompt,
      String requiredMessage,
      Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.promptRequiredText(
        reader, prompt, requiredMessage, cancelledExceptionFactory);
  }

  public static <X extends RuntimeException> String promptOptionalText(
      LineReader reader, String prompt, Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.promptOptionalText(reader, prompt, cancelledExceptionFactory);
  }

  public static <X extends RuntimeException> BigDecimal promptPositiveAmount(
      LineReader reader,
      String prompt,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.promptPositiveAmount(
        reader, prompt, invalidMessage, cancelledExceptionFactory);
  }

  public static <X extends RuntimeException> BigInteger promptOptionalInteger(
      LineReader reader,
      String prompt,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.promptOptionalInteger(
        reader, prompt, invalidMessage, cancelledExceptionFactory);
  }

  public static <X extends RuntimeException> BigDecimal promptOptionalDecimal(
      LineReader reader,
      String prompt,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.promptOptionalDecimal(
        reader, prompt, invalidMessage, cancelledExceptionFactory);
  }

  public static <X extends RuntimeException> boolean promptYesNo(
      LineReader reader,
      String prompt,
      boolean defaultValue,
      String invalidMessage,
      Supplier<X> cancelledExceptionFactory) {
    return TerminalPrompts.promptYesNo(
        reader, prompt, defaultValue, invalidMessage, cancelledExceptionFactory);
  }

  public static String rootMessage(Throwable ex) {
    return TerminalPrompts.rootMessage(ex);
  }

  public static String cInfo(String text) {
    return TerminalText.cInfo(text);
  }

  public static String cOk(String text) {
    return TerminalText.cOk(text);
  }

  public static String cError(String text) {
    return TerminalText.cError(text);
  }

  public static String cEmph(String text) {
    return TerminalText.cEmph(text);
  }

  public static String cPlain(String text) {
    return TerminalText.cPlain(text);
  }

  public static String cMuted(String text) {
    return TerminalText.cMuted(text);
  }

  public static String cWarn(String text) {
    return TerminalText.cWarn(text);
  }

  public static String cRule() {
    return TerminalText.cRule();
  }

  public static int selectMenu(
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int cancelIndex,
      String errorContext) {
    return TerminalMenu.selectMenu(titleLines, options, footerLines, cancelIndex, errorContext);
  }

  public static int selectMenu(
      String title, String[] options, String footer, int cancelIndex, String errorContext) {
    return TerminalMenu.selectMenu(title, options, footer, cancelIndex, errorContext);
  }
}
