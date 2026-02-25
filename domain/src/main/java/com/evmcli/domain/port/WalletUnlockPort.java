package com.evmcli.domain.port;

public interface WalletUnlockPort {
  String unlockPrivateKeyHex(String walletName, char[] password);
}
