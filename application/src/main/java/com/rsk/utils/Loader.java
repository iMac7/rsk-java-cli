package com.rsk.utils;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Loader {
  private static final char[] FRAMES = {'|', '/', '-', '\\'};

  private Loader() {}

  public static <T> T runWithSpinner(String message, CheckedSupplier<T> supplier) {
    AtomicBoolean running = new AtomicBoolean(true);
    Thread spinner =
        new Thread(
            () -> {
              int index = 0;
              while (running.get()) {
                System.out.print("\r" + FRAMES[index % FRAMES.length] + " " + message);
                System.out.flush();
                index++;
                try {
                  Thread.sleep(120L);
                } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            },
            "rsk-cli-loader");
    spinner.setDaemon(true);
    spinner.start();

    try {
      try {
        return supplier.get();
      } catch (RuntimeException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IllegalStateException(ex.getMessage(), ex);
      }
    } finally {
      running.set(false);
      spinner.interrupt();
      try {
        spinner.join(250L);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      int clearWidth = message.length() + 4;
      System.out.print("\r" + " ".repeat(clearWidth) + "\r");
      System.out.flush();
    }
  }

  @FunctionalInterface
  public interface CheckedSupplier<T> {
    T get() throws Exception;
  }
}
