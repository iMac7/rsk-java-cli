package com.rsk.commands.simulate;

import static com.rsk.utils.Terminal.*;

import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Format;
import com.rsk.utils.Rns;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.fusesource.jansi.Ansi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;

public class Helpers {
  private static final Logger LOGGER = LoggerFactory.getLogger(Helpers.class);
  private final com.rsk.commands.balance.Helpers balanceHelpers;
  private final com.rsk.commands.wallet.Helpers walletHelpers;
  private final com.rsk.commands.contract.Helpers contractHelpers;

  public Helpers(
      com.rsk.commands.balance.Helpers balanceHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers,
      com.rsk.commands.contract.Helpers contractHelpers) {
    this.balanceHelpers = balanceHelpers;
    this.walletHelpers = walletHelpers;
    this.contractHelpers = contractHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(
        new com.rsk.commands.balance.Helpers(
            new com.rsk.commands.config.Helpers(new com.rsk.utils.Storage.JsonConfigRepository(homeDir)),
            com.rsk.commands.wallet.Helpers.defaultHelpers(),
            new com.rsk.utils.Rpc.Web3jRpcGateway()),
        com.rsk.commands.wallet.Helpers.defaultHelpers(),
        com.rsk.commands.contract.Helpers.defaultHelpers());
  }

  public ChainProfile resolveChain(boolean mainnet, boolean testnet, String chain, String chainUrl) {
    return balanceHelpers.resolveChain(mainnet, testnet, chain, chainUrl);
  }

  public WalletMetadata resolveWallet(String walletName) {
    if (walletName != null && !walletName.isBlank()) {
      return walletHelpers.requireWallet(walletName);
    }
    return walletHelpers
        .activeWallet()
        .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
  }

  public String resolveAddressInput(ChainProfile chainProfile, String value) {
    return balanceHelpers.resolveAddressInput(chainProfile, value);
  }

  public BigInteger resolveGasPriceWei(Web3j web3j, BigDecimal gasPriceRbtc) throws Exception {
    if (gasPriceRbtc != null) {
      return Format.decimalToUnits(gasPriceRbtc, 18);
    }
    return web3j.ethGasPrice().send().getGasPrice();
  }

  public void simulateRbtc(
      ChainProfile chainProfile,
      Web3j web3j,
      WalletMetadata walletMeta,
      String toAddress,
      BigDecimal value,
      BigInteger gasLimit,
      BigDecimal gasPriceRbtc,
      String data)
      throws Exception {
    BigInteger valueWei = Format.decimalToUnits(value, 18);
    BigInteger balanceWei =
        web3j.ethGetBalance(walletMeta.address(), DefaultBlockParameterName.LATEST).send().getBalance();
    BigInteger gasPriceWei = resolveGasPriceWei(web3j, gasPriceRbtc);

    boolean standardTransfer = data == null || data.isBlank();
    boolean txValid = true;
    String estimationWarning = null;
    BigInteger estimatedGas;
    try {
      EthEstimateGas estimate =
          web3j
              .ethEstimateGas(
                  Transaction.createFunctionCallTransaction(
                      walletMeta.address(), null, gasPriceWei, null, toAddress, valueWei, data == null ? "" : data))
              .send();
      if (estimate.hasError() || estimate.getAmountUsed() == null) {
        throw new IllegalStateException(
            estimate.getError() == null ? "Gas estimation failed" : estimate.getError().getMessage());
      }
      estimatedGas = estimate.getAmountUsed();
    } catch (Exception ex) {
      estimatedGas = gasLimit != null ? gasLimit : BigInteger.valueOf(21_000L);
      estimationWarning = rootMessage(ex);
      txValid = standardTransfer;
    }
    if (gasLimit != null) {
      estimatedGas = gasLimit;
    }
    if (standardTransfer && estimatedGas.compareTo(BigInteger.valueOf(21_000L)) < 0) {
      txValid = false;
      estimationWarning = "Provided gas limit is below the standard 21000 gas required for RBTC transfers.";
    }

    BigInteger gasCostWei = estimatedGas.multiply(gasPriceWei);
    BigInteger totalCostWei = valueWei.add(gasCostWei);
    boolean sufficientBalance = balanceWei.compareTo(totalCostWei) >= 0;
    BigInteger balanceAfterWei = balanceWei.subtract(totalCostWei);
    boolean simulationWarning = sufficientBalance && txValid && estimationWarning != null;

    printHeader(walletMeta.address(), toAddress, value.toPlainString() + " " + chainProfile.nativeSymbol());
    System.out.println(cInfo("Network: ") + chainProfile.name());
    System.out.println(cInfo("Transaction Type: ") + "RBTC Transfer");
    System.out.println(cInfo("Transfer Amount: ") + value.toPlainString() + " " + chainProfile.nativeSymbol());
    System.out.println(
        cInfo("Current Balance: ")
            + unitsToDecimal(balanceWei, 18).toPlainString()
            + " "
            + chainProfile.nativeSymbol());
    System.out.println(cInfo("Estimated Gas: ") + estimatedGas);
    System.out.println(
        cInfo("Gas Price: ")
            + new BigDecimal(gasPriceWei).movePointLeft(9).stripTrailingZeros().toPlainString()
            + " Gwei");
    System.out.println(
        cInfo("Total Gas Cost: ")
            + unitsToDecimal(gasCostWei, 18).toPlainString()
            + " "
            + chainProfile.nativeSymbol());
    System.out.println(
        cInfo("Balance After: ")
            + unitsToDecimal(balanceAfterWei.max(BigInteger.ZERO), 18).toPlainString()
            + " "
            + chainProfile.nativeSymbol());
    System.out.println(
        cInfo("Total Transaction Cost: ")
            + unitsToDecimal(totalCostWei, 18).toPlainString()
            + " "
            + chainProfile.nativeSymbol());
    printValidationHeader();
    printValidation(
        "Sufficient Balance",
        sufficientBalance,
        "Enough RBTC for transfer + gas",
        "Insufficient RBTC for transfer + gas");
    if (simulationWarning) {
      printValidationWarning(
          "Transaction Validity",
          "Node gas estimation unavailable; using "
              + estimatedGas
              + " gas fallback"
              + (estimationWarning == null || estimationWarning.isBlank()
                  ? "."
                  : " (" + estimationWarning + ")"));
    } else {
      printValidation(
          "Transaction Validity",
          txValid,
          "Transaction simulation successful",
          estimationWarning == null || estimationWarning.isBlank()
              ? "Gas estimation failed; unable to validate transaction."
              : estimationWarning);
    }
    printFooter(sufficientBalance && txValid, simulationWarning);
  }

  public void simulateErc20(
      ChainProfile chainProfile,
      Web3j web3j,
      WalletMetadata walletMeta,
      String toAddress,
      String token,
      BigDecimal value,
      BigInteger gasLimit,
      BigDecimal gasPriceRbtc,
      String data)
      throws Exception {
    if (!Rns.isHexAddress(token)) {
      throw new IllegalArgumentException("Invalid token address: " + token);
    }

    BigInteger gasPriceWei = resolveGasPriceWei(web3j, gasPriceRbtc);
    BigInteger rbtcBalanceWei =
        web3j.ethGetBalance(walletMeta.address(), DefaultBlockParameterName.LATEST).send().getBalance();

    Function nameFn = new Function("name", List.of(), List.of(TypeReference.create(Utf8String.class)));
    Function symbolFn = new Function("symbol", List.of(), List.of(TypeReference.create(Utf8String.class)));
    Function decimalsFn = new Function("decimals", List.of(), List.of(TypeReference.create(Uint8.class)));
    Function balanceOfFn =
        new Function(
            "balanceOf",
            List.of(new Address(walletMeta.address())),
            List.of(TypeReference.create(Uint256.class)));

    String tokenName = "Unknown";
    String tokenSymbol = "TOKEN";
    int decimals = 18;
    BigInteger tokenBalanceUnits = BigInteger.ZERO;
    try {
      List<Type> nameOut = contractHelpers.executeReadFunction(chainProfile, token, "name", List.of(), List.of(TypeReference.create(Utf8String.class)));
      if (!nameOut.isEmpty()) {
        tokenName = ((Utf8String) nameOut.get(0)).getValue();
      }
    } catch (Exception ex) {
      logTokenReadFallback("name", token, ex);
    }
    try {
      List<Type> symbolOut = contractHelpers.executeReadFunction(chainProfile, token, "symbol", List.of(), List.of(TypeReference.create(Utf8String.class)));
      if (!symbolOut.isEmpty()) {
        tokenSymbol = ((Utf8String) symbolOut.get(0)).getValue();
      }
    } catch (Exception ex) {
      logTokenReadFallback("symbol", token, ex);
    }
    try {
      List<Type> decOut = contractHelpers.executeReadFunction(chainProfile, token, "decimals", List.of(), List.of(TypeReference.create(Uint8.class)));
      if (!decOut.isEmpty()) {
        decimals = ((Uint8) decOut.get(0)).getValue().intValue();
      }
    } catch (Exception ex) {
      logTokenReadFallback("decimals", token, ex);
    }
    try {
      List<Type> balOut = contractHelpers.executeReadFunction(
          chainProfile,
          token,
          "balanceOf",
          List.of(new Address(walletMeta.address())),
          List.of(TypeReference.create(Uint256.class)));
      if (!balOut.isEmpty()) {
        tokenBalanceUnits = ((Uint256) balOut.get(0)).getValue();
      }
    } catch (Exception ex) {
      logTokenReadFallback("balanceOf", token, ex);
    }

    BigInteger amountUnits = Format.decimalToUnits(value, decimals);
    Function transferFn =
        new Function(
            "transfer",
            List.of(new Address(toAddress), new Uint256(amountUnits)),
            List.of(TypeReference.create(Bool.class)));
    String callData = (data != null && !data.isBlank()) ? data : FunctionEncoder.encode(transferFn);

    boolean txValid = true;
    try {
      var callResp =
          web3j
              .ethCall(
                  Transaction.createEthCallTransaction(walletMeta.address(), token, callData),
                  DefaultBlockParameterName.LATEST)
              .send();
      if (callResp.hasError()) {
        throw new IllegalStateException(callResp.getError().getMessage());
      }
    } catch (Exception ex) {
      txValid = false;
    }

    BigInteger estimatedGas;
    try {
      EthEstimateGas estimate =
          web3j
              .ethEstimateGas(
                  Transaction.createFunctionCallTransaction(
                      walletMeta.address(), null, gasPriceWei, null, token, BigInteger.ZERO, callData))
              .send();
      if (estimate.hasError() || estimate.getAmountUsed() == null) {
        throw new IllegalStateException(
            estimate.getError() == null ? "Gas estimation failed" : estimate.getError().getMessage());
      }
      estimatedGas = estimate.getAmountUsed();
    } catch (Exception ex) {
      estimatedGas = gasLimit != null ? gasLimit : BigInteger.valueOf(100_000L);
      txValid = false;
    }
    if (gasLimit != null) {
      estimatedGas = gasLimit;
    }

    BigInteger gasCostWei = estimatedGas.multiply(gasPriceWei);
    boolean sufficientToken = tokenBalanceUnits.compareTo(amountUnits) >= 0;
    boolean sufficientGas = rbtcBalanceWei.compareTo(gasCostWei) >= 0;
    BigInteger tokenAfter = tokenBalanceUnits.subtract(amountUnits);
    BigInteger rbtcAfter = rbtcBalanceWei.subtract(gasCostWei);

    printHeader(walletMeta.address(), toAddress, value.toPlainString() + " tokens");
    System.out.println(cInfo("Network: ") + chainProfile.name());
    System.out.println(cInfo("Transaction Type: ") + "ERC20 Token Transfer");
    System.out.println(cInfo("Transfer Amount: ") + value.toPlainString() + " " + tokenSymbol);
    System.out.println(
        cInfo("Current Balance: ")
            + unitsToDecimal(tokenBalanceUnits, decimals).toPlainString()
            + " "
            + tokenSymbol);
    System.out.println(cInfo("Token Name: ") + tokenName);
    System.out.println(cInfo("Token Contract: ") + token);
    System.out.println(cInfo("Estimated Gas: ") + estimatedGas);
    System.out.println(
        cInfo("Gas Price: ")
            + new BigDecimal(gasPriceWei).movePointLeft(9).stripTrailingZeros().toPlainString()
            + " Gwei");
    System.out.println(
        cInfo("Total Gas Cost: ")
            + unitsToDecimal(gasCostWei, 18).toPlainString()
            + " "
            + chainProfile.nativeSymbol());
    System.out.println(
        cInfo("RBTC Balance After Gas: ")
            + unitsToDecimal(rbtcAfter.max(BigInteger.ZERO), 18).toPlainString()
            + " "
            + chainProfile.nativeSymbol());
    System.out.println(
        cInfo("Token Balance After Transfer: ")
            + unitsToDecimal(tokenAfter.max(BigInteger.ZERO), decimals).toPlainString()
            + " "
            + tokenSymbol);
    printValidationHeader();
    printValidation(
        "Sufficient Token Balance",
        sufficientToken,
        "Enough tokens for transfer",
        "Insufficient token balance");
    printValidation(
        "Sufficient Gas Balance",
        sufficientGas,
        "Enough RBTC for gas",
        "Insufficient RBTC for gas");
    printValidation(
        "Transaction Validity",
        txValid,
        "Transaction simulation successful",
        "Simulation call or gas estimation failed");
    printFooter(sufficientToken && sufficientGas && txValid, false);
  }

  private static void printHeader(String from, String to, String amount) {
    System.out.println(cEmph("Simulating Transaction"));
    System.out.println(cInfo("From Address: ") + from);
    System.out.println(cInfo("To Address: ") + to);
    System.out.println(cInfo("Amount: ") + amount);
    System.out.println();
    System.out.println(cEmph("SIMULATION RESULTS"));
    System.out.println();
  }

  private static void printValidationHeader() {
    System.out.println();
    System.out.println(cEmph("VALIDATION RESULTS"));
    System.out.println();
  }

  private static void printFooter(boolean ok, boolean warning) {
    System.out.println();
    if (ok && !warning) {
      System.out.println(cOk("Transaction simulation successful! Transaction is ready to execute."));
    } else if (ok) {
      System.out.println(
          cWarn("Transaction simulation completed with warnings. Review the gas fallback before executing."));
    } else {
      System.out.println(
          Ansi.ansi().fg(Ansi.Color.RED).a("Simulation indicates this transaction may fail.").reset());
    }
  }

  private static void printValidation(String label, boolean ok, String okText, String failText) {
    System.out.println(
        cInfo(label + ": ")
            + (ok ? cOk(okText) : Ansi.ansi().fg(Ansi.Color.RED).a(failText).reset()));
  }

  private static void printValidationWarning(String label, String warningText) {
    System.out.println(cInfo(label + ": ") + cWarn(warningText));
  }

  private static BigDecimal unitsToDecimal(BigInteger units, int decimals) {
    return new BigDecimal(units).movePointLeft(decimals);
  }

  private static void logTokenReadFallback(String functionName, String tokenAddress, Exception ex) {
    LOGGER.warn(
        "Unable to read token {} via {}. Falling back to default simulation values.",
        tokenAddress,
        functionName,
        ex);
    System.err.println("Unable to read token " + ex);
  }

}
