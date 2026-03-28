package com.rsk.utils;

import com.rsk.commands.config.CliConfig;
import com.rsk.utils.Chain.ChainProfile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Optional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;


public final class Transaction {
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
      Web3j web3j = Rpc.web3j(chainProfile);
      Credentials credentials = Credentials.create(privateKeyHex);
      BigInteger nonce =
          web3j
              .ethGetTransactionCount(
                  credentials.getAddress(), DefaultBlockParameterName.PENDING)
              .send()
              .getTransactionCount();

      RawTransaction tx =
          RawTransaction.createTransaction(
              nonce,
              request.gasPriceWei(),
              request.gasLimit(),
              request.to(),
              request.valueWei(),
              request.data() == null ? "" : request.data());
      byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
      var sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
      if (sent.hasError()) {
        throw new IllegalStateException(sent.getError().getMessage());
      }
      return new PendingTransaction(credentials.getAddress(), sent.getTransactionHash());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to submit transaction", ex);
    }
  }

  public static TransactionReceipt waitForReceipt(
      ChainProfile chainProfile, String txHash, int maxPolls, long sleepMs) {
    try {
      Web3j web3j = Rpc.web3j(chainProfile);
      for (int i = 0; i < maxPolls; i++) {
        var receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
        if (receiptResponse.getTransactionReceipt().isPresent()) {
          return receiptResponse.getTransactionReceipt().get();
        }
        Thread.sleep(sleepMs);
      }
      throw new IllegalStateException("Timed out waiting for transaction receipt: " + txHash);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch transaction receipt", ex);
    }
  }

  public static TransactionReceipt waitForSuccessfulReceipt(
      ChainProfile chainProfile, String txHash, int maxPolls, long sleepMs) {
    TransactionReceipt receipt = waitForReceipt(chainProfile, txHash, maxPolls, sleepMs);
    if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
      throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
    }
    return receipt;
  }

  public static Optional<TxReceiptDetails> receiptDetails(ChainProfile chainProfile, String txHash) {
    try {
      Web3j web3j = Rpc.web3j(chainProfile);
      Optional<TransactionReceipt> receipt =
          web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
      return receipt.map(
          value ->
              new TxReceiptDetails(
                  txHash,
                  value.getBlockHash(),
                  value.getBlockNumber() == null ? null : value.getBlockNumber().toString(),
                  value.getGasUsed() == null ? null : value.getGasUsed().toString(),
                  value.getStatus(),
                  value.getFrom(),
                  value.getTo()));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch transaction receipt", ex);
    }
  }

  public static BigInteger currentBlockNumber(ChainProfile chainProfile) {
    try {
      return Rpc.web3j(chainProfile).ethBlockNumber().send().getBlockNumber();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch current block number", ex);
    }
  }

  public static String explorerTxUrl(ChainProfile chainProfile, String txHash) {
    return Chain.explorerUrl(chainProfile, txHash, true);
  }

  public static String networkDisplayName(ChainProfile chainProfile) {
    if (chainProfile.chainId() == 30L) {
      return "Rootstock Mainnet";
    }
    if (chainProfile.chainId() == 31L) {
      return "Rootstock Testnet";
    }
    return chainProfile.name();
  }

  private static CliConfig loadConfig() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)).loadConfig();
  }

  public record SendRequest(
      String to, BigInteger valueWei, BigInteger gasLimit, BigInteger gasPriceWei, String data) {}

  public record PendingTransaction(String fromAddress, String txHash) {}
}
