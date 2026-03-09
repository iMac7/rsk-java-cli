package com.evmcli.application;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import java.math.BigDecimal;
import java.math.BigInteger;

public class BalanceService {
  private static final BigDecimal WEI = new BigDecimal("1000000000000000000");
  private final Rpc.RpcPort rpcPort;

  public BalanceService(Rpc.RpcPort rpcPort) {
    this.rpcPort = rpcPort;
  }

  public BigInteger nativeBalanceWei(ChainProfile chainProfile, String address) {
    return rpcPort.getNativeBalance(chainProfile, address);
  }

  public BigDecimal toNative(BigInteger wei) {
    return new BigDecimal(wei).divide(WEI);
  }
}
