package com.rsk.commands.verify;

import com.rsk.utils.Chain.ChainProfile;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "verify", description = "Verify contract", mixinStandardHelpOptions = true)
  public static class VerifyCommand implements Callable<Integer> {
    @Option(
        names = "--json",
        required = true,
        paramLabel = "<path>",
        description = "Path to Standard JSON Input file")
    String jsonPath;

    @Option(names = "--name", required = true, paramLabel = "<name>", description = "Contract name")
    String contractName;

    @Option(names = "--address", required = true, paramLabel = "<address>", description = "Deployed contract address")
    String address;

    @Option(
        names = "--compiler-version",
        required = true,
        paramLabel = "<version>",
        description = "Compiler version, e.g. v0.8.17+commit...")
    String compilerVersion;

    @Option(names = "--license-type", defaultValue = "mit", description = "License type")
    String licenseType;

    @Option(names = "--autodetect-constructor-args", defaultValue = "true")
    boolean autodetectConstructorArgs;

    @Option(
        names = "--constructor-args",
        paramLabel = "<hex>",
        description = "Hex-encoded constructor args if autodetect is false")
    String constructorArgs;

    @Option(
        names = "--decodedArgs",
        description = "Deprecated alias. Use --constructor-args hex value")
    List<String> decodedArgs;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use chains.mainnet")
      boolean mainnet;

      @Option(names = "--testnet", description = "Use chains.testnet")
      boolean testnet;

      @Option(
          names = "--chain",
          paramLabel = "<name>",
          description = "Use config chain key, e.g. chains.custom.<name> or <name>")
      String chain;

      @Option(names = "--chainurl", paramLabel = "<url>", description = "Use an explicit RPC URL")
      String chainUrl;
    }

    @Override
    public Integer call() {
      HELPERS.validateVerifyInput(address, autodetectConstructorArgs, constructorArgs, decodedArgs);

      ChainProfile chainProfile =
          HELPERS.resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      System.out.printf("Initializing verification on %s...%n", chainProfile.name());
      System.out.println("Reading JSON Standard Input from " + jsonPath + "...");
      System.out.println("Verifying contract " + contractName + " deployed at " + address + "...");
      if (!autodetectConstructorArgs) {
        System.out.println("Using constructor arguments: " + constructorArgs);
      }

      HELPERS.submitVerification(
          chainProfile,
          jsonPath,
          contractName,
          address,
          compilerVersion,
          licenseType,
          autodetectConstructorArgs,
          constructorArgs);

      System.out.println("Contract verification request sent.");
      System.out.println("Verification submitted successfully.");
      System.out.println("Explorer: " + HELPERS.blockscoutAddressUrl(chainProfile, address));
      return 0;
    }
  }
}
