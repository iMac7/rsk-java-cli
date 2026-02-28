package com.evmcli.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class EvmCliCommandHelpTest {
  @Test
  @DisplayName("wallet help lists active and dump subcommands")
  void walletHelpListsActiveAndDumpSubcommands(@TempDir java.nio.file.Path homeDir) {
    CliContext context = new CliContext(homeDir);
    CommandLine commandLine = new CommandLine(new EvmCliCommand(context));
    CommandLine walletCommand = commandLine.getSubcommands().get("wallet");

    StringWriter out = new StringWriter();
    walletCommand.usage(new PrintWriter(out));

    assertThat(out.toString()).contains("active");
    assertThat(out.toString()).contains("dump");
  }

  @Test
  @DisplayName("balance without target suggests setting active wallet")
  void balanceWithoutTargetSuggestsActiveWallet(@TempDir java.nio.file.Path homeDir) {
    CliContext context = new CliContext(homeDir);
    CommandLine commandLine = new CommandLine(new EvmCliCommand(context));
    StringWriter err = new StringWriter();
    commandLine.setErr(new PrintWriter(err));

    int exitCode = commandLine.execute("balance");

    assertThat(exitCode).isNotZero();
    assertThat(err.toString()).contains("set an active wallet");
  }

  @Test
  @DisplayName("resolve reverse validates address format")
  void resolveReverseValidatesAddressFormat(@TempDir java.nio.file.Path homeDir) {
    CliContext context = new CliContext(homeDir);
    CommandLine commandLine = new CommandLine(new EvmCliCommand(context));
    StringWriter err = new StringWriter();
    commandLine.setErr(new PrintWriter(err));

    int exitCode = commandLine.execute("resolve", "not-an-address", "--reverse");

    assertThat(exitCode).isNotZero();
    assertThat(err.toString()).contains("Invalid address format for reverse resolution");
  }
}
