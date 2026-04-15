package com.rsk.utils;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class TerminalClipboard {
  private TerminalClipboard() {}

  static void copyToClipboard(String value) {
    try {
      if (GraphicsEnvironment.isHeadless()) {
        throw new IllegalStateException("Clipboard access is unavailable in this environment.");
      }
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to copy to clipboard. Clipboard access is unavailable.", ex);
    }
  }
}
