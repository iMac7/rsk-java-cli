package com.evmcli.application;

import com.evmcli.domain.model.WalletMetadata;
import com.evmcli.domain.port.WalletPort;
import java.util.List;

public class WalletService {
  private final WalletPort walletPort;

  public WalletService(WalletPort walletPort) {
    this.walletPort = walletPort;
  }

  public WalletMetadata create(String name, char[] password) {
    return walletPort.createWallet(name, password);
  }

  public WalletMetadata importPrivateKey(String name, String privateKeyHex, char[] password) {
    return walletPort.importWallet(name, privateKeyHex, password);
  }

  public List<WalletMetadata> list() {
    return walletPort.listWallets();
  }

  public void switchActive(String name) {
    walletPort.switchActiveWallet(name);
  }

  public void rename(String oldName, String newName) {
    walletPort.renameWallet(oldName, newName);
  }

  public void delete(String name) {
    walletPort.deleteWallet(name);
  }
}
