package com.rsk.commands.balance;

import static com.rsk.utils.Terminal.*;
import static com.rsk.utils.Format.formatAmount;

import com.rsk.java_cli.CliHelpers;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Terminal;
import java.math.BigInteger;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public class Subcommands {
  private static final LineReader PROMPT_READER = createPromptReader();
  private static final String[] TOKEN_MENU_ITEMS = {"rBTC", "RIF", "USDRIF", "DoC", "Custom Token"};

  private Subcommands() {}

  private static LineReader createPromptReader() {
    if (Terminal.interactiveTerminal() != null) {
      return LineReaderBuilder.builder().terminal(Terminal.interactiveTerminal()).build();
    }
    return LineReaderBuilder.builder().build();
  }

  @Command(name = "balance", description = "Get native balance", mixinStandardHelpOptions = true)
  public static class BalanceCommand implements Callable<Integer> {
    @Spec CommandSpec spec;
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    Target target;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    NetworkOptions networkOptions = new NetworkOptions();

    @Option(
        names = "--token",
        paramLabel = "<symbol|address>",
        description = "Token symbol (RIF, USDRIF, DoC, rBTC) or ERC20 contract address")
    String token;

    static class Target {
      @Option(names = "--wallet", paramLabel = "<name>", description = "Wallet name")
      String wallet;

      @Option(names = "--address", paramLabel = "<address>", description = "Address or RNS name")
      String address;

      @Option(names = "--rns", paramLabel = "<name>", description = "RNS name")
      String rns;
    }

    static class NetworkOptions {
      @Option(names = "--mainnet", description = "Use rootstock mainnet")
      boolean mainnet;

      @Option(names = "--testnet", description = "Use rootstock testnet")
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
          helpers().resolveChain(
              networkOptions.mainnet,
              networkOptions.testnet,
              networkOptions.chain,
              networkOptions.chainUrl);

      String walletName = target == null ? null : target.wallet;
      String addressInput = target == null ? null : target.address;
      String rns = target == null ? null : target.rns;
      String address =
          rns != null && !rns.isBlank()
              ? helpers().resolveAddressInput(chainProfile, rns)
              : walletName != null && !walletName.isBlank()
                  ? helpers().resolveWalletAddress(walletName)
                  : addressInput != null && !addressInput.isBlank()
                      ? helpers().resolveAddressInput(chainProfile, addressInput)
                      : helpers().resolveActiveWalletAddress();

      String selectedToken = token == null || token.isBlank() ? selectToken() : token.trim();
      if (selectedToken == null) {
        return 0;
      }

      if (isNativeToken(selectedToken)) {
        printSuccess("Select token to check balance: rBTC");
        printNativeBalance(chainProfile, address);
        return 0;
      }

      if (looksLikeAddress(selectedToken)) {
        printSuccess("Select token to check balance: Custom Token");
        printCustomTokenBalance(chainProfile, address, selectedToken);
        return 0;
      }

      if ("custom".equalsIgnoreCase(selectedToken) || "Custom Token".equalsIgnoreCase(selectedToken)) {
        printSuccess("Select token to check balance: Custom Token");
        printCustomTokenBalance(chainProfile, address, promptForCustomTokenAddress(chainProfile));
        return 0;
      }

      String normalizedToken = normalizeKnownTokenSymbol(selectedToken);
      printSuccess("Select token to check balance: " + normalizedToken);
      printKnownTokenBalance(chainProfile, address, normalizedToken);
      return 0;
    }

    private String selectToken() {
      int selectedIndex = selectMenu("Select token to check balance:", TOKEN_MENU_ITEMS);
      return selectedIndex < 0 ? null : TOKEN_MENU_ITEMS[selectedIndex];
    }

    private int selectMenu(String title, String[] menuItems) {
      return Terminal.selectMenu(title, menuItems, null, -1, "balance");
    }

    private void printNativeBalance(ChainProfile chainProfile, String address) {
      BigInteger wei;
      try {
        wei = helpers().nativeBalanceWei(chainProfile, address);
      } catch (Exception ex) {
        throw new IllegalStateException(
            "Unable to fetch balance on network '" + chainProfile.name() + "'.", ex);
      }

      printSuccess("Balance retrieved successfully");
      System.out.println(cInfo("📄 Wallet Address: ") + address.toLowerCase());
      System.out.println(cInfo("🌐 Network: ") + helpers().networkDisplayName(chainProfile));
      System.out.println(
          cInfo("💰 Current Balance: ")
              + formatAmount(helpers().toNative(wei))
              + " "
              + chainProfile.nativeSymbol());
      System.out.println(cInfo("👍 Ensure that transactions are being conducted on the correct network."));
    }

    private void printKnownTokenBalance(ChainProfile chainProfile, String holderAddress, String tokenSymbol) {
      String contractAddress = helpers().knownTokenAddress(tokenSymbol, chainProfile);
      printTokenBalance(chainProfile, holderAddress, contractAddress);
    }

    private void printCustomTokenBalance(ChainProfile chainProfile, String holderAddress, String initialValue) {
      String contractAddress = initialValue;
      while (true) {
        try {
          String validated = helpers().validateTokenAddress(contractAddress);
          printTokenBalance(chainProfile, holderAddress, validated);
          return;
        } catch (IllegalArgumentException ex) {
          printError(ex.getMessage());
          contractAddress = promptForCustomTokenAddress(chainProfile);
        }
      }
    }

    private String promptForCustomTokenAddress(ChainProfile chainProfile) {
      while (true) {
        try {
          String value =
              PROMPT_READER.readLine(
                  Ansi.ansi()
                      .fg(Ansi.Color.GREEN)
                      .a("✔ ")
                      .reset()
                      .fg(Ansi.Color.WHITE)
                      .a("Enter the token address")
                      .fgRgb(140, 140, 140)
                      .a(" [0x...]")
                      .reset()
                      .a(": ")
                      .toString());
          if (value == null) {
            throw new IllegalStateException("Balance check cancelled.");
          }
          String trimmed = value.trim();
          helpers().validateTokenAddress(trimmed);
          return trimmed;
        } catch (UserInterruptException ex) {
          throw new IllegalStateException("Balance check cancelled.");
        } catch (IllegalArgumentException ex) {
          printError(ex.getMessage());
        }
      }
    }

    private void printTokenBalance(ChainProfile chainProfile, String holderAddress, String contractAddress) {
      Helpers.TokenBalance tokenBalance = helpers().tokenBalance(chainProfile, holderAddress, contractAddress);
      printSuccess("Balance retrieved successfully");
      System.out.println(cInfo("📄 Token Information:"));
      System.out.printf("         Name: %s%n", tokenBalance.name());
      System.out.printf("         Contract: %s%n", tokenBalance.contractAddress());
      System.out.printf("      👤 Holder Address: %s%n", holderAddress.toLowerCase());
      System.out.printf(
          "      💰 Balance: %s %s%n",
          formatAmount(helpers().tokenUnitsToDecimal(tokenBalance.balance(), tokenBalance.decimals())),
          tokenBalance.symbol());
      System.out.printf("      🌐 Network: %s%n", helpers().networkDisplayName(chainProfile));
      System.out.println(cInfo("👍 Ensure that transactions are being conducted on the correct network."));
    }
    private boolean isNativeToken(String value) {
      return "rbtc".equalsIgnoreCase(value);
    }

    private boolean looksLikeAddress(String value) {
      return value != null && value.startsWith("0x");
    }

    private String normalizeKnownTokenSymbol(String value) {
      if ("rif".equalsIgnoreCase(value)) {
        return "RIF";
      }
      if ("usdrif".equalsIgnoreCase(value)) {
        return "USDRIF";
      }
      if ("doc".equalsIgnoreCase(value)) {
        return "DoC";
      }
      return value;
    }

    private void printSuccess(String message) {
      System.out.println(cOk("✔ " + message));
    }

    private void printError(String message) {
      System.out.println(cError("❌ " + message));
    }
    private Helpers helpers() {
      return CliHelpers.deps(spec).balanceHelpers();
    }
  }

}
