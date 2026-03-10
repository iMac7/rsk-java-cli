package com.rsk.utils;

import com.rsk.utils.Chain.ChainProfile;
import java.math.BigInteger;
import java.util.Optional;
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

public final class Rpc {
  private Rpc() {}

  public interface RpcPort {
    BigInteger getNativeBalance(ChainProfile chainProfile, String address);

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
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        EthGetBalance response =
            web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
        return response.getBalance();
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to fetch balance", ex);
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
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
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
          throw new IllegalStateException(sent.getError().getMessage());
        }
        return sent.getTransactionHash();
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to send transfer", ex);
      }
    }

    @Override
    public Optional<String> getTransactionReceiptStatus(ChainProfile chainProfile, String txHash) {
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        Optional<TransactionReceipt> receipt =
            web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
        return receipt.map(TransactionReceipt::getStatus);
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to fetch transaction receipt", ex);
      }
    }

    @Override
    public Optional<TxReceiptDetails> getTransactionReceiptDetails(ChainProfile chainProfile, String txHash) {
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

    @Override
    public BigInteger getCurrentBlockNumber(ChainProfile chainProfile) {
      try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
        EthBlockNumber response = web3j.ethBlockNumber().send();
        return response.getBlockNumber();
      } catch (Exception ex) {
        throw new IllegalStateException("Unable to fetch current block number", ex);
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
