package com.evmcli.application;

import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.commands.wallet.Helpers.WalletPort;
import com.rsk.commands.wallet.Helpers.WalletUnlockPort;
import java.util.List;
import java.util.Optional;

public class WalletService {
  private final WalletPort walletPort;
  private final WalletUnlockPort walletUnlockPort;

  public WalletService(WalletPort walletPort, WalletUnlockPort walletUnlockPort) {
    this.walletPort = walletPort;
    this.walletUnlockPort = walletUnlockPort;
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

  public Optional<WalletMetadata> active() {
    return walletPort.getActiveWallet();
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

  public String dumpPrivateKey(String name, char[] password) {
    return walletUnlockPort.unlockPrivateKeyHex(name, password);
  }
}
