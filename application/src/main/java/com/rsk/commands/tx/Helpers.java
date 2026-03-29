package com.rsk.commands.tx;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import com.rsk.utils.Rpc.TxReceiptDetails;
import java.math.BigInteger;
import java.util.Optional;

public class Helpers {
  private final Rpc.RpcPort rpcPort;

  public Helpers(Rpc.RpcPort rpcPort) {
    this.rpcPort = rpcPort;
  }

  public static Helpers defaultHelpers() {
    return new Helpers(new Rpc.Web3jRpcGateway());
  }

  public Optional<String> receiptStatus(ChainProfile chainProfile, String txHash) {
    return receiptDetails(chainProfile, txHash).map(TxReceiptDetails::status);
  }

  public Optional<TxReceiptDetails> receiptDetails(ChainProfile chainProfile, String txHash) {
    return rpcPort.getTransactionReceiptDetails(chainProfile, txHash);
  }

  public BigInteger currentBlockNumber(ChainProfile chainProfile) {
    return rpcPort.getCurrentBlockNumber(chainProfile);
  }
}
