package com.rsk.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fusesource.jansi.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public final class Terminal {
  private static final boolean UNICODE_SYMBOLS = detectUnicodeSymbols();
  private static final org.jline.terminal.Terminal MENU_TERMINAL = createInteractiveTerminal();

  private Terminal() {}

  public static String pick(String unicodeValue, String asciiValue) {
    return UNICODE_SYMBOLS ? unicodeValue : asciiValue;
  }

  public static org.jline.terminal.Terminal interactiveTerminal() {
    return MENU_TERMINAL;
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

    int renderedLines = 0;
    int selectedIndex = 0;

    try {
      Attributes originalAttributes = MENU_TERMINAL.enterRawMode();
      NonBlockingReader reader = MENU_TERMINAL.reader();

      try {
        while (true) {
          renderedLines = renderMenu(titleLines, options, footerLines, selectedIndex, renderedLines);
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

  private static int renderMenu(
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int selectedIndex,
      int renderedLines) {
    if (renderedLines > 0) {
      System.out.print("\u001b[" + renderedLines + "F");
    }

    int lines = 0;

    for (String titleLine : titleLines) {
      if (titleLine == null || titleLine.isBlank()) {
        System.out.println();
      } else {
        System.out.println(cTitle(titleLine));
      }
      lines++;
    }

    for (int i = 0; i < options.length; i++) {
      String pointer = i == selectedIndex ? pick("❯ ", "> ") : "  ";
      if (i == selectedIndex) {
        System.out.println(cSelected(pointer + options[i]));
      } else {
        System.out.println(cPlain(pointer + options[i]));
      }
      lines++;
    }

    for (String footerLine : footerLines) {
      if (footerLine == null || footerLine.isBlank()) {
        System.out.println();
      } else {
        System.out.println(cFooter(footerLine));
      }
      lines++;
    }

    System.out.flush();
    return lines;
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

  private static String cPlain(String text) {
    return Ansi.ansi().fg(Ansi.Color.WHITE).a(text).reset().toString();
  }

  private static String cFooter(String text) {
    return Ansi.ansi().fgRgb(140, 140, 140).a(text).reset().toString();
  }
}
