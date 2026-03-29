package com.rsk.utils;

import com.rsk.utils.Chain.ChainProfile;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Rpc {
  private static final Logger LOGGER = LoggerFactory.getLogger(Rpc.class);
  private static final ConcurrentMap<String, Web3j> CLIENTS_BY_RPC_URL = new ConcurrentHashMap<>();

  private Rpc() {}

  public static Web3j web3j(ChainProfile chainProfile) {
    String rpcUrl = chainProfile.rpcUrl();
    if (rpcUrl == null || rpcUrl.isBlank()) {
      throw new IllegalArgumentException("RPC URL is required.");
    }
    return CLIENTS_BY_RPC_URL.computeIfAbsent(rpcUrl, url -> Web3j.build(new HttpService(url)));
  }

  public interface RpcPort {
    BigInteger getNativeBalance(ChainProfile chainProfile, String address);

    BigInteger gasPriceWei(ChainProfile chainProfile);

    String sendNativeTransfer(
        ChainProfile chainProfile,
        String privateKeyHex,
        String to,
        BigInteger valueWei,
        BigInteger gasLimit,
        BigInteger gasPriceWei,
        String data);

    Optional<String> getTransactionReceiptStatus(ChainProfile chainProfile, String txHash);

    Optional<TxReceiptDetails> getTransactionReceiptDetails(ChainProfile chainProfile, String txHash);

    BigInteger getCurrentBlockNumber(ChainProfile chainProfile);
  }

  public static class Web3jRpcGateway implements RpcPort {
    @Override
    public BigInteger getNativeBalance(ChainProfile chainProfile, String address) {
      try {
        Web3j web3j = web3j(chainProfile);
        EthGetBalance response =
            web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
        return response.getBalance();
      } catch (Exception ex) {
        LOGGER.error(
            "Unable to fetch balance for address {} on chain {}",
            address,
            chainProfile.chainId(),
            ex);
        throw new IllegalStateException("Unable to fetch balance", ex);
      }
    }

    @Override
    public BigInteger gasPriceWei(ChainProfile chainProfile) {
      try {
        return web3j(chainProfile).ethGasPrice().send().getGasPrice();
      } catch (Exception ex) {
        LOGGER.error("Unable to fetch gas price on chain {}", chainProfile.chainId(), ex);
        throw new IllegalStateException("Unable to fetch gas price", ex);
      }
    }

    @Override
    public String sendNativeTransfer(
        ChainProfile chainProfile,
        String privateKeyHex,
        String to,
        BigInteger valueWei,
        BigInteger gasLimit,
        BigInteger gasPriceWei,
        String data) {
      try {
        Web3j web3j = web3j(chainProfile);
        Credentials credentials = Credentials.create(privateKeyHex);
        EthGetTransactionCount nonceResponse =
            web3j
                .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                .send();

        RawTransaction tx =
            RawTransaction.createTransaction(
                nonceResponse.getTransactionCount(),
                gasPriceWei,
                gasLimit,
                to,
                valueWei,
                data == null ? "" : data);
        byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
        String payload = Numeric.toHexString(signed);

        EthSendTransaction sent = web3j.ethSendRawTransaction(payload).send();
        if (sent.hasError()) {
          LOGGER.warn(
              "Raw transaction submission failed on chain {}: {}",
              chainProfile.chainId(),
              sent.getError().getMessage());
          throw new IllegalStateException(sent.getError().getMessage());
        }
        return sent.getTransactionHash();
      } catch (Exception ex) {
        LOGGER.error(
            "Unable to send transfer to {} on chain {}",
            to,
            chainProfile.chainId(),
            ex);
        throw new IllegalStateException("Unable to send transfer", ex);
      }
    }

    @Override
    public Optional<String> getTransactionReceiptStatus(ChainProfile chainProfile, String txHash) {
      try {
        Optional<TransactionReceipt> receipt =
            web3j(chainProfile).ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
        return receipt.map(TransactionReceipt::getStatus);
      } catch (Exception ex) {
        LOGGER.error(
            "Unable to fetch transaction receipt status for {} on chain {}",
            txHash,
            chainProfile.chainId(),
            ex);
        throw new IllegalStateException("Unable to fetch transaction receipt", ex);
      }
    }

    @Override
    public Optional<TxReceiptDetails> getTransactionReceiptDetails(
        ChainProfile chainProfile, String txHash) {
      return mapTransactionReceipt(
          chainProfile,
          txHash,
          value ->
              new TxReceiptDetails(
                  txHash,
                  value.getBlockHash(),
                  value.getBlockNumber() == null ? null : value.getBlockNumber().toString(),
                  value.getGasUsed() == null ? null : value.getGasUsed().toString(),
                  value.getStatus(),
                  value.getFrom(),
                  value.getTo()));
    }

    @Override
    public BigInteger getCurrentBlockNumber(ChainProfile chainProfile) {
      try {
        EthBlockNumber response = web3j(chainProfile).ethBlockNumber().send();
        return response.getBlockNumber();
      } catch (Exception ex) {
        LOGGER.error("Unable to fetch current block number on chain {}", chainProfile.chainId(), ex);
        throw new IllegalStateException("Unable to fetch current block number", ex);
      }
    }

    private <T> Optional<T> mapTransactionReceipt(
        ChainProfile chainProfile, String txHash, Function<TransactionReceipt, T> mapper) {
      try {
        Optional<TransactionReceipt> receipt =
            web3j(chainProfile).ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
        return receipt.map(mapper);
      } catch (Exception ex) {
        LOGGER.error(
            "Unable to fetch transaction receipt details for {} on chain {}",
            txHash,
            chainProfile.chainId(),
            ex);
        throw new IllegalStateException("Unable to fetch transaction receipt", ex);
      }
    }
  }

  public record TxReceiptDetails(
      String txHash,
      String blockHash,
      String blockNumber,
      String gasUsed,
      String status,
      String from,
      String to) {}
}
