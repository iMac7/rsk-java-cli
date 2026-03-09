package com.evmcli.application;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import java.util.Optional;

public class TxService {
  private final Rpc.RpcPort rpcPort;

  public TxService(Rpc.RpcPort rpcPort) {
    this.rpcPort = rpcPort;
  }

  public Optional<String> receiptStatus(ChainProfile chainProfile, String txHash) {
    return rpcPort.getTransactionReceiptStatus(chainProfile, txHash);
  }
}
