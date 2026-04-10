package com.rsk.commands.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.rsk.java_cli.CliHelpers;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SubcommandsLiveTest {
  private static final String RESET = "\u001b[0m";
  private static final String CYAN = "\u001b[36m";
  private static final String GREEN = "\u001b[32m";
  private static final String YELLOW = "\u001b[33m";
  private static final String PETER_RNS = "peter.rsk";
  private static final String PETER_RNS_ADDRESS = "0x8d03c5f405e8902796a7f2cd7435a32a45ef251a";

  @Test
  void resolveCommandResolvesPeterRskForwardAndReverseOnTestnet() {
    String forward = runCommand("resolve", "--testnet", PETER_RNS);
    assertThat(forward).isEqualTo(PETER_RNS_ADDRESS);

    String reverse = runCommand("resolve", "--testnet", "--reverse", PETER_RNS_ADDRESS);
    assertThat(reverse).isEqualTo(PETER_RNS);
  }

  private static String runCommand(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      originalOut.println(
          CYAN + "[live-test] running: rsk-cli> " + String.join(" ", args) + RESET);
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      int exitCode = CliHelpers.createCommandLine().execute(args);
      assertThat(exitCode).isZero();
      String result = out.toString(StandardCharsets.UTF_8).trim();
      originalOut.println(GREEN + "[live-test] output: " + result + RESET);
      originalOut.println(YELLOW + "[live-test] command completed" + RESET);
      return result;
    } finally {
      System.setOut(originalOut);
    }
  }
}
