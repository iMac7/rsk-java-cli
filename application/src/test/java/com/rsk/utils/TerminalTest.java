package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TerminalTest {
  @Test
  void withClearedPasswordClearsPasswordAfterSuccess() {
    char[] password = "secret".toCharArray();

    String result = Terminal.withClearedPassword(password, String::new);

    assertThat(result).isEqualTo("secret");
    assertThat(password).containsOnly('\0');
  }

  @Test
  void withClearedPasswordClearsPasswordAfterFailure() {
    char[] password = "secret".toCharArray();

    assertThatThrownBy(
            () ->
                Terminal.withClearedPassword(
                    password,
                    ignored -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
    assertThat(password).containsOnly('\0');
  }
}
