package com.rsk.commands.transfer;

import com.rsk.commands.config.Helpers.ChainResolutionSupport;
import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.commands.wallet.Helpers.WalletUnlockPort;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Contract;
import com.rsk.utils.Rpc;
import com.rsk.utils.Storage;
import com.rsk.utils.Transaction;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import org.web3j.crypto.Credentials;

public class Helpers extends ChainResolutionSupport {
  private final com.rsk.commands.wallet.Helpers walletHelpers;
  private final WalletUnlockPort walletUnlockPort;
  private final Rpc.RpcPort rpcPort;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers,
      WalletUnlockPort walletUnlockPort,
      Rpc.RpcPort rpcPort) {
    super(configHelpers);
    this.walletHelpers = walletHelpers;
    this.walletUnlockPort = walletUnlockPort;
    this.rpcPort = rpcPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    Storage.JsonWalletRepository walletRepository = new Storage.JsonWalletRepository(homeDir);
    return new Helpers(
        new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)),
        com.rsk.commands.wallet.Helpers.defaultHelpers(),
        walletRepository,
        new Rpc.Web3jRpcGateway());
  }

  public String resolveWalletName(String walletName) {
    if (walletName != null && !walletName.isBlank()) {
      return walletName;
    }
    return walletHelpers
        .activeWallet()
        .map(WalletMetadata::name)
        .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
  }

  public String walletAddress(String walletName) {
    return walletHelpers.requireWallet(walletName).address();
  }

  public BigDecimal nativeBalance(ChainProfile chainProfile, String address) {
    return new BigDecimal(rpcPort.getNativeBalance(chainProfile, address))
        .divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);
  }

  public void validateWalletPassword(String walletName, char[] password) {
    walletUnlockPort.withUnlockedCredentials(walletName, password, credentials -> null);
  }

  public PendingTransfer sendNative(
      ChainProfile chainProfile,
      String walletName,
      char[] password,
      String to,
      BigInteger valueWei,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    return walletUnlockPort.withUnlockedCredentials(
        walletName,
        password,
        credentials ->
            sendNativeWithCredentials(
                chainProfile, credentials, to, valueWei, gasLimit, gasPriceWei, data));
  }

  public PendingTransfer sendNativeWithCredentials(
      ChainProfile chainProfile,
      Credentials credentials,
      String to,
      BigInteger valueWei,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    try {
      Transaction.PendingTransaction pending =
          Transaction.submit(
              chainProfile,
              credentials,
              new Transaction.SendRequest(to, valueWei, gasLimit, gasPriceWei, data));
      return new PendingTransfer(pending.fromAddress(), pending.txHash());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to send transfer", ex);
    }
  }

  public PendingTransfer sendToken(
      ChainProfile chainProfile,
      String walletName,
      char[] password,
      String tokenAddress,
      String to,
      BigDecimal value,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    return walletUnlockPort.withUnlockedCredentials(
        walletName,
        password,
        credentials -> {
          try {
            Contract.TokenMetadata metadata = Contract.readTokenMetadata(chainProfile, tokenAddress);
            BigInteger amountUnits = Contract.tokenAmountToUnits(value, metadata.decimals());
            String encodedData =
                data != null && !data.isBlank()
                    ? data
                    : Contract.encodeErc20Transfer(to, amountUnits);

            BigInteger resolvedGasLimit = gasLimit;
            if (resolvedGasLimit == null) {
              resolvedGasLimit =
                  Contract.estimateTokenTransferGas(
                      chainProfile, walletAddress(walletName), tokenAddress, encodedData, gasPriceWei);
            }
            Transaction.PendingTransaction pending =
                Transaction.submit(
                    chainProfile,
                    credentials,
                    new Transaction.SendRequest(
                        tokenAddress, BigInteger.ZERO, resolvedGasLimit, gasPriceWei, encodedData));
            return new PendingTransfer(pending.fromAddress(), pending.txHash());
          } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Invalid token amount for token decimals.", ex);
          } catch (Exception ex) {
            throw new IllegalStateException("Unable to send token transfer", ex);
          }
        });
  }

  public String resolveTokenAddress(ChainProfile chainProfile, String tokenOption) {
    return Contract.resolveTokenAddress(tokenOption, chainProfile);
  }

  public record TransferRequest(String recipient, BigDecimal amount) {}

  public record PendingTransfer(String walletAddress, String txHash) {}
}
