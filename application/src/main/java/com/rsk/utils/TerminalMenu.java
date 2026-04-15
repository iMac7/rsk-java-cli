package com.rsk.utils;

import java.util.ArrayList;
import java.util.List;
import org.jline.terminal.Attributes;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.NonBlockingReader;

final class TerminalMenu {
  private TerminalMenu() {}

  static int selectMenu(
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int cancelIndex,
      String errorContext) {
    org.jline.terminal.Terminal terminal = TerminalSession.interactiveTerminal();
    if (terminal == null) {
      throw new IllegalStateException("Unable to initialize " + errorContext + " terminal.");
    }

    int selectedIndex = 0;
    int windowStart = 0;

    try {
      Attributes originalAttributes = terminal.enterRawMode();
      NonBlockingReader reader = terminal.reader();
      Display display = new Display(terminal, false);

      try {
        while (true) {
          windowStart =
              clampWindowStart(terminal, titleLines, options, footerLines, selectedIndex, windowStart);
          renderMenu(terminal, display, titleLines, options, footerLines, selectedIndex, windowStart);
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
        terminal.writer().println();
        terminal.puts(org.jline.utils.InfoCmp.Capability.cursor_visible);
        terminal.setAttributes(originalAttributes);
        terminal.writer().flush();
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to render " + errorContext + " menu.", ex);
    }
  }

  static int selectMenu(
      String title, String[] options, String footer, int cancelIndex, String errorContext) {
    return selectMenu(
        title == null ? List.of() : List.of(title),
        options,
        footer == null || footer.isBlank() ? List.of() : List.of(footer),
        cancelIndex,
        errorContext);
  }

  private static void renderMenu(
      org.jline.terminal.Terminal terminal,
      Display display,
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int selectedIndex,
      int windowStart) {
    List<AttributedString> rendered = new ArrayList<>();
    int terminalRows = Math.max(1, terminal.getSize().getRows());
    int reservedRows = titleLines.size() + footerLines.size();
    int availableOptionRows = Math.max(1, terminalRows - reservedRows);
    int indicatorRows = options.length > availableOptionRows ? 2 : 0;
    int visibleOptions = Math.max(1, availableOptionRows - indicatorRows);
    int windowSize = Math.min(options.length - windowStart, visibleOptions);
    int windowEnd = Math.min(options.length, windowStart + windowSize);

    for (String titleLine : titleLines) {
      rendered.add(
          AttributedString.fromAnsi(
              titleLine == null || titleLine.isBlank() ? "" : TerminalText.cTitle(titleLine)));
    }

    if (windowStart > 0) {
      rendered.add(AttributedString.fromAnsi(TerminalText.cFooter("\u2191 more")));
    }

    for (int i = windowStart; i < windowEnd; i++) {
      String pointer = i == selectedIndex ? TerminalText.pick("\u276F ", "> ") : "  ";
      String line = pointer + options[i];
      rendered.add(
          AttributedString.fromAnsi(
              i == selectedIndex ? TerminalText.cSelected(line) : TerminalText.cPlain(line)));
    }

    if (windowEnd < options.length) {
      rendered.add(AttributedString.fromAnsi(TerminalText.cFooter("\u2193 more")));
    }

    for (String footerLine : footerLines) {
      rendered.add(
          AttributedString.fromAnsi(
              footerLine == null || footerLine.isBlank() ? "" : TerminalText.cFooter(footerLine)));
    }

    display.resize(terminal.getSize().getRows(), terminal.getSize().getColumns());
    display.update(rendered, -1);
  }

  private static int clampWindowStart(
      org.jline.terminal.Terminal terminal,
      List<String> titleLines,
      String[] options,
      List<String> footerLines,
      int selectedIndex,
      int currentWindowStart) {
    int terminalRows = Math.max(1, terminal.getSize().getRows());
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
}
