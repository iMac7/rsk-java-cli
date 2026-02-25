package com.evmcli.application;

import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.port.RpcPort;
import com.evmcli.domain.port.WalletUnlockPort;
import java.math.BigInteger;

public class TransferService {
  private final RpcPort rpcPort;
  private final WalletUnlockPort walletUnlockPort;

  public TransferService(RpcPort rpcPort, WalletUnlockPort walletUnlockPort) {
    this.rpcPort = rpcPort;
    this.walletUnlockPort = walletUnlockPort;
  }

  public String sendNative(
      ChainProfile chainProfile,
      String walletName,
      char[] password,
      String to,
      BigInteger valueWei,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    String privateKeyHex = walletUnlockPort.unlockPrivateKeyHex(walletName, password);
    return rpcPort.sendNativeTransfer(
        chainProfile, privateKeyHex, to, valueWei, gasLimit, gasPriceWei, data);
  }
}
