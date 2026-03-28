package com.rsk.java_cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CliTest {
  @Test
  void splitArgsSupportsSingleQuotedArguments() {
    assertThat(Cli.splitArgs("tx --note 'hello world'"))
        .containsExactly("tx", "--note", "hello world");
  }

  @Test
  void splitArgsSupportsEscapedQuotesInsideQuotedArguments() {
    assertThat(Cli.splitArgs("tx --note \"say \\\"hi\\\"\""))
        .containsExactly("tx", "--note", "say \"hi\"");
  }

  @Test
  void splitArgsSupportsEscapedWhitespaceOutsideQuotes() {
    assertThat(Cli.splitArgs("wallet create --name test\\ wallet"))
        .containsExactly("wallet", "create", "--name", "test wallet");
  }

  @Test
  void splitArgsRejectsUnclosedSingleQuotes() {
    assertThatThrownBy(() -> Cli.splitArgs("tx --note 'hello"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unclosed quote in command.");
  }
}
