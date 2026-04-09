package com.rsk.commands.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

class HelpersTest {
  private final Helpers helpers = new Helpers(null, null);

  @Test
  void buildDeploymentDataPrefixesBytecodeWhenThereIsNoConstructor() {
    String abi = "[{\"type\":\"function\",\"name\":\"ping\",\"inputs\":[]}]";

    String deploymentData = helpers.buildDeploymentData("60006000", abi, List.of());

    assertThat(deploymentData).isEqualTo("0x60006000");
  }

  @Test
  void buildDeploymentDataAppendsEncodedConstructorArguments() {
    String abi =
        """
        [
          {
            "type":"constructor",
            "inputs":[
              {"name":"owner","type":"address"},
              {"name":"label","type":"string"},
              {"name":"enabled","type":"bool"},
              {"name":"supply","type":"uint256"}
            ]
          }
        ]
        """;
    List<String> args =
        List.of(
            "0x1111111111111111111111111111111111111111",
            "Rootstock",
            "true",
            "42");
    List<Type> expectedArgs =
        List.of(
            new Address("0x1111111111111111111111111111111111111111"),
            new Utf8String("Rootstock"),
            new Bool(true),
            new Uint256(BigInteger.valueOf(42L)));

    String deploymentData = helpers.buildDeploymentData("0x60006000", abi, args);

    assertThat(deploymentData)
        .isEqualTo("0x60006000" + Numeric.cleanHexPrefix(FunctionEncoder.encodeConstructor(expectedArgs)));
  }

  @Test
  void buildDeploymentDataRejectsConstructorArgumentCountMismatch() {
    String abi =
        """
        [
          {
            "type":"constructor",
            "inputs":[{"name":"owner","type":"address"}]
          }
        ]
        """;

    assertThatThrownBy(() -> helpers.buildDeploymentData("0x60", abi, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Constructor argument count mismatch. Expected 1 but got 0.");
  }
}
