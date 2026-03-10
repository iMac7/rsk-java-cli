package com.rsk.utils;

import com.rsk.utils.Chain.ChainProfile;
import java.math.BigInteger;
import java.util.Optional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;


public final class Transaction {
  private Transaction() {}

  public static PendingTransaction submit(
      ChainProfile chainProfile, String privateKeyHex, SendRequest request) {
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
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
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
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

  public static Optional<TxReceiptDetails> receiptDetails(ChainProfile chainProfile, String txHash) {
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
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
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
      return web3j.ethBlockNumber().send().getBlockNumber();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch current block number", ex);
    }
  }

  public static String explorerTxUrl(ChainProfile chainProfile, String txHash) {
    String template = chainProfile.explorerTxUrlTemplate();
    if (template == null || template.isBlank()) {
      return "(explorer URL not configured)";
    }
    if (template.contains("%s")) {
      return String.format(template, txHash);
    }
    return template.endsWith("/") ? template + txHash : template + "/" + txHash;
  }

  public record SendRequest(
      String to, BigInteger valueWei, BigInteger gasLimit, BigInteger gasPriceWei, String data) {}

  public record PendingTransaction(String fromAddress, String txHash) {}

  public record TxReceiptDetails(
      String txHash,
      String blockHash,
      String blockNumber,
      String gasUsed,
      String status,
      String from,
      String to) {}
}
