package com.rsk.commands.verify;

import static com.rsk.utils.Terminal.*;

import com.rsk.java_cli.CliHelpers;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainProfile;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public class Subcommands {
  private static final Path EXAMPLE_JSON_PATH =
      Path.of(
              "application",
              "src",
              "main",
              "resources",
              "owner_contract",
              "Owner_compilation_details.json")
          .toAbsolutePath()
          .normalize();

  private Subcommands() {}

  @Command(name = "verify", description = "Verify a contract", mixinStandardHelpOptions = true)
  public static class VerifyCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    @Option(
        names = "--json",
        paramLabel = "<path>",
        description = "Path to the JSON Standard Input")
    String jsonPath;

    @Option(names = "--example", description = "Use the bundled Owner example verification JSON")
    boolean example;

    @Option(names = "--name", required = true, paramLabel = "<name>", description = "Name of the contract")
    String contractName;

    @Option(
        names = {"-a", "--address"},
        required = true,
        paramLabel = "<address>",
        description = "Address of the deployed contract")
    String address;

    @Override
    public Integer call() {
      helpers().validateVerifyInput(address);
      String resolvedJsonPath = resolveJsonPath();

      ChainProfile chainProfile = helpers().resolveChain(false, false, null, null);

      System.out.println();
      System.out.println(cEmph("Initializing verification on " + chainProfile.name() + "..."));
      System.out.println(cInfo("Reading JSON Standard Input from ") + resolvedJsonPath + "...");
      System.out.println(cInfo("Verifying contract ") + contractName + cInfo(" deployed at ") + address + "...");

      helpers().submitVerification(chainProfile, resolvedJsonPath, contractName, address);

      System.out.println();
      System.out.println(cRule());
      System.out.println(cOk("Contract verification request sent."));
      System.out.println(cOk("Verification submitted successfully."));
      System.out.println(cMuted("Explorer: " + Chain.blockscoutAddressUrl(chainProfile, address)));
      System.out.println(cRule());
      return 0;
    }

    private String resolveJsonPath() {
      if (example) {
        return EXAMPLE_JSON_PATH.toString();
      }
      if (jsonPath == null || jsonPath.isBlank()) {
        throw new IllegalArgumentException("Provide --json <path> or use --example.");
      }
      return jsonPath.trim();
    }

    private Helpers helpers() {
      return CliHelpers.deps(spec).verifyHelpers();
    }
  }
}
