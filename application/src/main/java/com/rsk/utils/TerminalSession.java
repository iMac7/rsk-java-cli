package com.rsk.utils;

import java.nio.charset.StandardCharsets;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.terminal.TerminalBuilder;

final class TerminalSession {
  private static final org.jline.terminal.Terminal INTERACTIVE_TERMINAL =
      createInteractiveTerminal();
  private static final org.jline.terminal.Terminal PROMPT_TERMINAL = createPromptTerminal();
  private static final LineReader PASSWORD_READER = createPasswordReader();

  private TerminalSession() {}

  static org.jline.terminal.Terminal interactiveTerminal() {
    return INTERACTIVE_TERMINAL;
  }

  static org.jline.terminal.Terminal promptTerminal() {
    return PROMPT_TERMINAL;
  }

  static LineReader createPromptReader(Parser parser) {
    LineReaderBuilder builder = LineReaderBuilder.builder();
    if (promptTerminal() != null) {
      builder.terminal(promptTerminal());
    }
    if (parser != null) {
      builder.parser(parser);
    }
    return builder.build();
  }

  static LineReader passwordReader() {
    return PASSWORD_READER;
  }

  private static org.jline.terminal.Terminal createInteractiveTerminal() {
    try {
      return TerminalBuilder.builder()
          .system(true)
          .dumb(false)
          .encoding(StandardCharsets.UTF_8)
          .build();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static org.jline.terminal.Terminal createPromptTerminal() {
    if (interactiveTerminal() != null) {
      return interactiveTerminal();
    }
    try {
      return TerminalBuilder.builder()
          .system(false)
          .streams(System.in, System.out)
          .dumb(true)
          .encoding(StandardCharsets.UTF_8)
          .build();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static LineReader createPasswordReader() {
    if (interactiveTerminal() != null) {
      return LineReaderBuilder.builder().terminal(interactiveTerminal()).build();
    }
    return null;
  }
}
