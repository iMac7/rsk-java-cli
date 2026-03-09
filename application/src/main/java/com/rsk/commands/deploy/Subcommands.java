package com.rsk.commands.deploy;

import com.rsk.utils.Chain.ChainProfile;
import java.io.Console;
import java.util.List;
import java.util.concurrent.Callable;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

public class Subcommands {
  private static final Helpers HELPERS = Helpers.defaultHelpers();

  private Subcommands() {}

  @Command(name = "deploy", description = "Deploy contract", mixinStandardHelpOptions = true)
  public static class DeployCommand implements Callable<Integer> {
    @Option(names = "--abi", required = true, paramLabel = "<path>", description = "Path to ABI JSON file")
    String abiPath;

    @Option(
        names = "--bytecode",
        required = true,
        paramLabel = "<path>",
        description = "Path to bytecode file (.bin)")
    String bytecodePath;

    @Option(names = "--wallet", paramLabel = "<name>", description = "Wallet name")
    String walletName;

    @Option(names = "--args", arity = "0..*", description = "Constructor arguments")
    List<String> constructorArgs;

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
      ChainProfile chainProfile =
          HELPERS.resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      String selectedWallet =
          walletName != null && !walletName.isBlank() ? walletName : HELPERS.activeWalletName();
      char[] password = readPassword("Wallet password: ");
      String privateKeyHex = HELPERS.dumpPrivateKey(selectedWallet, password);
      String abiContent = HELPERS.readRequiredFile(abiPath, "ABI");
      String bytecodeContent = HELPERS.readRequiredFile(bytecodePath, "bytecode");
      String deploymentData = HELPERS.buildDeploymentData(bytecodeContent, abiContent, constructorArgs);

      Helpers.DeploymentResult result =
          HELPERS.deployContract(chainProfile, privateKeyHex, deploymentData);

      System.out.println("Contract deployment transaction sent.");
      System.out.println("Transaction Hash: " + result.txHash());
      System.out.println("Contract Address: " + result.contractAddress());
      System.out.println("Explorer: " + result.explorerUrl());
      return 0;
    }
  }

  static char[] readPassword(String prompt) {
    while (true) {
      try {
        Console console = System.console();
        if (console != null) {
          char[] password = console.readPassword(prompt);
          if (password == null || password.length == 0) {
            System.out.println("Password is required.");
            continue;
          }
          return password;
        }
        String password = LineReaderBuilder.builder().build().readLine(prompt, '*');
        if (password == null || password.isBlank()) {
          System.out.println("Password is required.");
          continue;
        }
        return password.toCharArray();
      } catch (UserInterruptException ex) {
        throw new IllegalStateException("Deployment cancelled.");
      } catch (RuntimeException ex) {
        if (Thread.currentThread().isInterrupted()) {
          Thread.interrupted();
          throw new IllegalStateException("Deployment cancelled.");
        }
        throw ex;
      }
    }
  }
}
