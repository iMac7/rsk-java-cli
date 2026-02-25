package com.evmcli.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WalletRegistry {
  private UUID activeWalletId;
  private List<WalletMetadata> wallets = new ArrayList<>();

  public UUID getActiveWalletId() {
    return activeWalletId;
  }

  public void setActiveWalletId(UUID activeWalletId) {
    this.activeWalletId = activeWalletId;
  }

  public List<WalletMetadata> getWallets() {
    return wallets;
  }

  public void setWallets(List<WalletMetadata> wallets) {
    this.wallets = wallets;
  }
}
