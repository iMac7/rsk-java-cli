package com.rsk.utils;

import com.rsk.commands.config.CliConfig;
import com.rsk.commands.config.Helpers;
import com.rsk.utils.Chain.ChainProfile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public final class Transaction {
  private static final Logger LOGGER = LoggerFactory.getLogger(Transaction.class);
  private static final BigDecimal WEI = new BigDecimal("1000000000000000000");
  private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

  private Transaction() {}

  public static BigInteger toWei(BigDecimal amount) {
    return amount.multiply(WEI).toBigIntegerExact();
  }

  public static BigInteger gasPriceRbtcToWei(BigDecimal gasPriceRbtc) {
    return gasPriceRbtc.multiply(WEI).toBigIntegerExact();
  }

  public static BigInteger defaultGasLimit() {
    return BigInteger.valueOf(loadConfig().getGas().getDefaultGasLimit());
  }

  public static BigInteger defaultGasPriceWei(ChainProfile chainProfile) {
    CliConfig config = loadConfig();
    long gasPriceGwei = config.getGas().getDefaultGasPriceGwei();
    if (gasPriceGwei <= 0L) {
      return new Rpc.Web3jRpcGateway().gasPriceWei(chainProfile);
    }
    return BigInteger.valueOf(gasPriceGwei).multiply(GWEI);
  }

  public static PendingTransaction submit(
      ChainProfile chainProfile, String privateKeyHex, SendRequest request) {
    try {
      Credentials credentials = Credentials.create(privateKeyHex);
      String txHash =
          new Rpc.Web3jRpcGateway()
              .sendNativeTransfer(
                  chainProfile,
                  privateKeyHex,
                  request.to(),
                  request.valueWei(),
                  request.gasLimit(),
                  request.gasPriceWei(),
                  request.data());
      return new PendingTransaction(credentials.getAddress(), txHash);
    } catch (Exception ex) {
      LOGGER.error("Unable to submit transaction on chain {}", chainProfile.chainId(), ex);
      throw new IllegalStateException("Unable to submit transaction", ex);
    }
  }

  public static TransactionReceipt waitForReceipt(
      ChainProfile chainProfile, String txHash, int maxPolls, long sleepMs) {
    try {
      Web3j web3j = Rpc.web3j(chainProfile);
      return waitForReceipt(web3j, txHash, maxPolls, sleepMs);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      LOGGER.error(
          "Unable to fetch transaction receipt {} on chain {}",
          txHash,
          chainProfile.chainId(),
          ex);
      throw new IllegalStateException("Unable to fetch transaction receipt", ex);
    }
  }

  public static TransactionReceipt waitForReceipt(
      Web3j web3j, String txHash, int maxPolls, long sleepMs) {
    try {
      for (int i = 0; i < maxPolls; i++) {
        var receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
        if (receiptResponse.getTransactionReceipt().isPresent()) {
          return receiptResponse.getTransactionReceipt().get();
        }
        Thread.sleep(sleepMs);
      }
      LOGGER.warn("Timed out waiting for transaction receipt {} after {} polls", txHash, maxPolls);
      throw new IllegalStateException("Timed out waiting for transaction receipt: " + txHash);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for transaction receipt", ex);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      LOGGER.error("Unable to fetch transaction receipt {}", txHash, ex);
      throw new IllegalStateException("Unable to fetch transaction receipt", ex);
    }
  }

  public static TransactionReceipt waitForSuccessfulReceipt(
      ChainProfile chainProfile, String txHash, int maxPolls, long sleepMs) {
    TransactionReceipt receipt = waitForReceipt(chainProfile, txHash, maxPolls, sleepMs);
    if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
      LOGGER.warn(
          "Transaction {} on chain {} completed with status {}",
          txHash,
          chainProfile.chainId(),
          receipt.getStatus());
      throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
    }
    return receipt;
  }

  public static TransactionReceipt waitForSuccessfulReceipt(
      Web3j web3j, String txHash, int maxPolls, long sleepMs) {
    TransactionReceipt receipt = waitForReceipt(web3j, txHash, maxPolls, sleepMs);
    if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
      LOGGER.warn(
          "Transaction {} completed with status {}",
          txHash,
          receipt.getStatus());
      throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
    }
    return receipt;
  }

  private static CliConfig loadConfig() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new Storage.JsonConfigRepository(homeDir)).loadConfig();
  }

  public record SendRequest(
      String to, BigInteger valueWei, BigInteger gasLimit, BigInteger gasPriceWei, String data) {}

  public record PendingTransaction(String fromAddress, String txHash) {}
}
