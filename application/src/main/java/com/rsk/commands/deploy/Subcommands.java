package com.rsk.commands.deploy;

import static com.rsk.utils.CliColors.*;

import com.rsk.utils.CliInput;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();
  //TODO is PROMPT_READER needed?
  private static final Path EXAMPLE_ABI_PATH =
      Path.of("application", "src", "main", "resources", "owner_contract", "Owner.abi")
          .toAbsolutePath()
          .normalize();
  private static final Path EXAMPLE_BYTECODE_PATH =
      Path.of("application", "src", "main", "resources", "owner_contract", "Owner.bin")
          .toAbsolutePath()
          .normalize();

  private Subcommands() {}

  @Command(name = "deploy", description = "Deploy a contract", mixinStandardHelpOptions = true)
  public static class DeployCommand implements Callable<Integer> {
    @Option(names = "--abi", paramLabel = "<path>", description = "Path to the ABI file")
    String abiPath;

    @Option(names = "--bytecode", paramLabel = "<path>", description = "Path to the bytecode file")
    String bytecodePath;

    @Option(names = "--wallet", paramLabel = "<wallet>", description = "Name of the wallet")
    String walletName;

    @Option(names = "--args", arity = "0..*", description = "Constructor arguments (space-separated)")
    List<String> constructorArgs;

    @Option(names = "--example", description = "Use the bundled Owner example contract")
    boolean example;

    @Option(names = {"-t", "--testnet"}, description = "Deploy on the testnet")
    boolean testnet;

    @Override
    public Integer call() {
      var chainProfile = HELPERS.resolveChain(false, testnet, null, null);
      System.out.println();
      System.out.println(cEmph("Initializing deployment for " + networkLabel(chainProfile) + "..."));

      String resolvedAbiPath = resolveAbiPath();
      String resolvedBytecodePath = resolveBytecodePath();
      String selectedWallet =
          walletName != null && !walletName.isBlank() ? walletName.trim() : HELPERS.activeWalletName();

      System.out.println(cInfo("Wallet: ") + selectedWallet);
      char[] password = readPassword("Enter your password to decrypt the wallet: ");
      String privateKeyHex = HELPERS.dumpPrivateKey(selectedWallet, password);
      String walletAddress = org.web3j.crypto.Credentials.create(privateKeyHex).getAddress();
      System.out.println(cInfo("Wallet account: ") + walletAddress);

      System.out.println(cInfo("Reading ABI from ") + resolvedAbiPath + "...");
      String abiContent = HELPERS.readRequiredFile(resolvedAbiPath, "ABI");

      System.out.println(cInfo("Reading bytecode from ") + resolvedBytecodePath + "...");
      String bytecodeContent = HELPERS.readRequiredFile(resolvedBytecodePath, "bytecode");

      List<String> resolvedArgs = resolveConstructorArgs(abiContent, constructorArgs);
      String deploymentData = HELPERS.buildDeploymentData(bytecodeContent, abiContent, resolvedArgs);
      Helpers.DeploymentResult result =
          HELPERS.deployContract(chainProfile, privateKeyHex, deploymentData);

      printDeploymentResult(chainProfile.name(), walletAddress, result);
      return 0;
    }

    private List<String> resolveConstructorArgs(String abiContent, List<String> providedArgs) {
      List<Helpers.ConstructorInput> constructorInputs = HELPERS.constructorInputs(abiContent);
      if (constructorInputs.isEmpty()) {
        return List.of();
      }

      if (providedArgs != null && !providedArgs.isEmpty()) {
        return providedArgs;
      }

      List<String> args = new ArrayList<>();
      System.out.println();
      System.out.println(cEmph("Constructor Arguments"));
      for (int i = 0; i < constructorInputs.size(); i++) {
        Helpers.ConstructorInput input = constructorInputs.get(i);
        String label = input.name().isBlank() ? "arg" + (i + 1) : input.name();
        args.add(promptRequiredText(label + " (" + input.type() + ")"));
      }
      return args;
    }

    private void printDeploymentResult(
        String networkName, String walletAddress, Helpers.DeploymentResult result) {
      System.out.println();
      System.out.println(cRule());
      System.out.println(cEmph("Contract Deployment Result"));
      System.out.println(cInfo("Network: ") + networkName);
      System.out.println(cInfo("Wallet: ") + walletAddress);
      System.out.println();
      System.out.println(cEmph("Deployment Receipt"));
      System.out.println(cPlain("  Transaction Hash: " + result.txHash()));
      System.out.println(cPlain("  Contract Address: " + result.contractAddress()));
      System.out.println();
      System.out.println(cMuted("Explorer: " + result.explorerUrl()));
      System.out.println(cRule());
    }

    private String networkLabel(com.rsk.utils.Chain.ChainProfile chainProfile) {
      if (chainProfile.chainId() == 31L) {
        return "testnet";
      }
      if (chainProfile.chainId() == 30L) {
        return "mainnet";
      }
      return chainProfile.name();
    }

    private String resolveAbiPath() {
      if (example) {
        return EXAMPLE_ABI_PATH.toString();
      }
      return requireText(abiPath, "Path to ABI file");
    }

    private String resolveBytecodePath() {
      if (example) {
        return EXAMPLE_BYTECODE_PATH.toString();
      }
      return requireText(bytecodePath, "Path to bytecode file");
    }
  }

  static char[] readPassword(String prompt) {
    return CliInput.readPassword(cOk("✔" + prompt), "Deployment cancelled.");
  }

  static String promptRequiredText(String label) {
    while (true) {
      try {
        Console console = System.console();
        String value;
        if (console != null) {
          value = console.readLine("%s", cPlain(label + ": "));
        } else {
          System.out.print(cPlain(label + ": "));
          System.out.flush();
          value = new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
        if (value == null) {
          throw new IllegalStateException("Deployment cancelled.");
        }
        if (!value.isBlank()) {
          return value.trim();
        }
        System.out.println(cError("Value is required."));
      } catch (IOException ex) {
        throw new IllegalStateException("Unable to read deployment input.", ex);
      } catch (UserInterruptException ex) {
        throw new IllegalStateException("Deployment cancelled.");
      }
    }
  }

  private static String requireText(String existingValue, String label) {
    if (existingValue != null && !existingValue.isBlank()) {
      return existingValue.trim();
    }
    return promptRequiredText(label);
  }
}

