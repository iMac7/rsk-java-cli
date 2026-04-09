package com.rsk.utils;

import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.fusesource.jansi.Ansi;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.NonBlockingReader;

public final class Terminal {
  private static final boolean UNICODE_SYMBOLS = detectUnicodeSymbols();
  private static final org.jline.terminal.Terminal MENU_TERMINAL = createInteractiveTerminal();
  private static final LineReader PASSWORD_READER = createPasswordReader();

  private Terminal() {}

  public static String pick(String unicodeValue, String asciiValue) {
    return UNICODE_SYMBOLS ? unicodeValue : asciiValue;
  }

  public static org.jline.terminal.Terminal interactiveTerminal() {
    return MENU_TERMINAL;
  }

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
        if (PASSWORD_READER == null) {
          throw new IllegalStateException(
              "Secure password entry requires a real terminal.");
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

  public static String cInfo(String text) {
    return Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString();
  }

  public static String cOk(String text) {
    return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
  }

  public static String cError(String text) {
    return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
  }

  public static String cEmph(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  public static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  public static String cMuted(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }

  public static String cWarn(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).a(text).reset().toString();
  }

  public static String cRule() {
    return Ansi.ansi()
        .fgRgb(140, 140, 140)
        .a("────────────────────────────────────────")
        .reset()
        .toString();
  }

  public static int selectMenu(
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int cancelIndex,
      String errorContext) {
    if (MENU_TERMINAL == null) {
      throw new IllegalStateException("Unable to initialize " + errorContext + " terminal.");
    }

    int selectedIndex = 0;
    int windowStart = 0;

    try {
      Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
      NonBlockingReader reader = MENU_TERMINAL.reader();
      Display display = new Display(MENU_TERMINAL, false);

      try {
        while (true) {
          windowStart = clampWindowStart(titleLines, options, footerLines, selectedIndex, windowStart);
          renderMenu(display, titleLines, options, footerLines, selectedIndex, windowStart);
          int key = reader.read();
          if (key < 0) {
            continue;
          }
          if (key == 3) {
            return cancelIndex;
          }
          if (key == 13 || key == 10) {
            return selectedIndex;
          }
          if (isForwardCycleKey(key)) {
            selectedIndex = moveDown(selectedIndex, options.length);
            continue;
          }
          if (isBackwardCycleKey(key)) {
            selectedIndex = moveUp(selectedIndex, options.length);
            continue;
          }
          if (key == 224 || key == 0) {
            selectedIndex = handleWindowsArrow(reader, selectedIndex, options.length);
            continue;
          }
          if (key == 27) {
            Integer updated = handleEscapeSequence(reader, selectedIndex, options.length);
            if (updated == null) {
              return cancelIndex;
            }
            selectedIndex = updated;
          }
        }
      } finally {
        display.update(List.of(AttributedString.fromAnsi("")), 0);
        MENU_TERMINAL.writer().println();
        MENU_TERMINAL.puts(org.jline.utils.InfoCmp.Capability.cursor_visible);
        MENU_TERMINAL.setAttributes(originalAttributes);
        MENU_TERMINAL.writer().flush();
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to render " + errorContext + " menu.", ex);
    }
  }

  public static int selectMenu(
      String title, String[] options, String footer, int cancelIndex, String errorContext) {
    return selectMenu(
        title == null ? List.of() : List.of(title),
        options,
        footer == null || footer.isBlank() ? List.of() : List.of(footer),
        cancelIndex,
        errorContext);
  }

  private static org.jline.terminal.Terminal createInteractiveTerminal() {
    try {
      return TerminalBuilder.builder().system(true).encoding(StandardCharsets.UTF_8).build();
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

  private static void renderMenu(
      Display display,
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int selectedIndex,
      int windowStart) {
    List<AttributedString> rendered = new ArrayList<>();
    int terminalRows = Math.max(1, MENU_TERMINAL.getSize().getRows());
    int reservedRows = titleLines.size() + footerLines.size();
    int availableOptionRows = Math.max(1, terminalRows - reservedRows);
    int indicatorRows = options.length > availableOptionRows ? 2 : 0;
    int visibleOptions = Math.max(1, availableOptionRows - indicatorRows);
    int windowSize = Math.min(options.length - windowStart, visibleOptions);
    int windowEnd = Math.min(options.length, windowStart + windowSize);

    for (String titleLine : titleLines) {
      rendered.add(AttributedString.fromAnsi(titleLine == null || titleLine.isBlank() ? "" : cTitle(titleLine)));
    }

    if (windowStart > 0) {
      rendered.add(AttributedString.fromAnsi(cFooter("↑ more")));
    }

    for (int i = windowStart; i < windowEnd; i++) {
      String pointer = i == selectedIndex ? pick("❯ ", "> ") : "  ";
      String line = pointer + options[i];
      rendered.add(AttributedString.fromAnsi(i == selectedIndex ? cSelected(line) : cPlain(line)));
    }

    if (windowEnd < options.length) {
      rendered.add(AttributedString.fromAnsi(cFooter("↓ more")));
    }

    for (String footerLine : footerLines) {
      rendered.add(AttributedString.fromAnsi(footerLine == null || footerLine.isBlank() ? "" : cFooter(footerLine)));
    }

    display.resize(MENU_TERMINAL.getSize().getRows(), MENU_TERMINAL.getSize().getColumns());
    display.update(rendered, -1);
  }

  private static int clampWindowStart(
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int selectedIndex,
      int currentWindowStart) {
    int terminalRows = Math.max(1, MENU_TERMINAL.getSize().getRows());
    int reservedRows = titleLines.size() + footerLines.size();
    int availableOptionRows = Math.max(1, terminalRows - reservedRows);
    int indicatorRows = options.length > availableOptionRows ? 2 : 0;
    int visibleOptions = Math.max(1, availableOptionRows - indicatorRows);
    int maxWindowStart = Math.max(0, options.length - visibleOptions);
    int windowStart = Math.max(0, Math.min(currentWindowStart, maxWindowStart));

    if (selectedIndex < windowStart) {
      windowStart = selectedIndex;
    } else if (selectedIndex >= windowStart + visibleOptions) {
      windowStart = selectedIndex - visibleOptions + 1;
    }

    return Math.max(0, Math.min(windowStart, maxWindowStart));
  }

  private static Integer handleEscapeSequence(
      NonBlockingReader reader, int selectedIndex, int itemCount) {
    try {
      int second = reader.read(25);
      if (second == NonBlockingReader.READ_EXPIRED || second < 0) {
        return null;
      }
      if (second == '[' || second == 'O') {
        int third = reader.read(25);
        if (third == 'A') {
          return moveUp(selectedIndex, itemCount);
        }
        if (third == 'B') {
          return moveDown(selectedIndex, itemCount);
        }
      }
      return null;
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to read keyboard escape sequence.", ex);
    }
  }

  private static int handleWindowsArrow(
      NonBlockingReader reader, int selectedIndex, int itemCount) {
    try {
      int scan = reader.read(25);
      if (scan == 72) {
        return moveUp(selectedIndex, itemCount);
      }
      if (scan == 80) {
        return moveDown(selectedIndex, itemCount);
      }
      return selectedIndex;
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to read keyboard scan code.", ex);
    }
  }

  private static int moveUp(int selectedIndex, int itemCount) {
    return (selectedIndex - 1 + itemCount) % itemCount;
  }

  private static int moveDown(int selectedIndex, int itemCount) {
    return (selectedIndex + 1) % itemCount;
  }

  private static boolean isForwardCycleKey(int key) {
    return key == 9 || key == 's' || key == 'S' || key == 'j' || key == 'J';
  }

  private static boolean isBackwardCycleKey(int key) {
    return key == 'w' || key == 'W' || key == 'k' || key == 'K';
  }

  private static boolean detectUnicodeSymbols() {
    String msystem = System.getenv("MSYSTEM");
    if (msystem != null && !msystem.isBlank()) {
      return false;
    }

    String shell = System.getenv("SHELL");
    if (shell != null && shell.toLowerCase().contains("bash")) {
      return false;
    }

    Charset charset = Charset.defaultCharset();
    return StandardCharsets.UTF_8.equals(charset);
  }

  private static String cTitle(String text) {
    return Ansi.ansi().fgRgb(255, 183, 77).bold().a(text).reset().toString();
  }

  private static String cSelected(String text) {
    return Ansi.ansi().fgRgb(255, 153, 51).bold().a(text).reset().toString();
  }

  private static String cFooter(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }
}
