package com.rsk.commands.tx;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import java.util.Optional;

public class Helpers {
  private final Rpc.RpcPort rpcPort;

  public Helpers(Rpc.RpcPort rpcPort) {
    this.rpcPort = rpcPort;
  }

  public Optional<String> receiptStatus(ChainProfile chainProfile, String txHash) {
    return rpcPort.getTransactionReceiptStatus(chainProfile, txHash);
  }
}
