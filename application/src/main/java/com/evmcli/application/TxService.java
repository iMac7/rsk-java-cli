package com.evmcli.application;

import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.port.RpcPort;
import java.util.Optional;

public class TxService {
  private final RpcPort rpcPort;

  public TxService(RpcPort rpcPort) {
    this.rpcPort = rpcPort;
  }

  public Optional<String> receiptStatus(ChainProfile chainProfile, String txHash) {
    return rpcPort.getTransactionReceiptStatus(chainProfile, txHash);
  }
}
