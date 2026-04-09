package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

class RnsTest {
  @Test
  void namehashReturnsZeroNodeForBlankName() throws Exception {
    assertThat(Numeric.toHexString(invokeNamehash("  ")))
        .isEqualTo(
            "0x0000000000000000000000000000000000000000000000000000000000000000");
  }

  @Test
  void namehashMatchesKnownEnsVector() throws Exception {
    assertThat(Numeric.toHexString(invokeNamehash("eth")))
        .isEqualTo(
            "0x93cdeb708b7545dc668eb9280176169d1c33cfd8ed6f04690a0bcc88a93fc4ae");
  }

  @Test
  void isValidRnsNameAcceptsNormalizedLabels() {
    assertThat(Rns.isValidRnsName(" Alice-01.RSK ")).isTrue();
  }

  @Test
  void isValidRnsNameRejectsMalformedNames() {
    assertThat(Rns.isValidRnsName(null)).isFalse();
    assertThat(Rns.isValidRnsName("alice")).isFalse();
    assertThat(Rns.isValidRnsName(".alice.rsk")).isFalse();
    assertThat(Rns.isValidRnsName("alice.rsk.")).isFalse();
    assertThat(Rns.isValidRnsName("alice..rsk")).isFalse();
    assertThat(Rns.isValidRnsName("-alice.rsk")).isFalse();
    assertThat(Rns.isValidRnsName("alice-.rsk")).isFalse();
    assertThat(Rns.isValidRnsName("a".repeat(64) + ".rsk")).isFalse();
  }

  private static byte[] invokeNamehash(String name) throws Exception {
    Method method = Rns.class.getDeclaredMethod("namehash", String.class);
    method.setAccessible(true);
    return (byte[]) method.invoke(null, name);
  }
}
