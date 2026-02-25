package com.evmcli.domain.port;

import com.evmcli.domain.model.ChainProfile;
import java.math.BigInteger;
import java.util.Optional;

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
}
