package com.evmcli.application;

import com.rsk.commands.wallet.Helpers.WalletUnlockPort;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import java.math.BigInteger;

public class TransferService {
  private final Rpc.RpcPort rpcPort;
  private final WalletUnlockPort walletUnlockPort;

  public TransferService(Rpc.RpcPort rpcPort, WalletUnlockPort walletUnlockPort) {
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
