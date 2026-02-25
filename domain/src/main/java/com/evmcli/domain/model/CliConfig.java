package com.evmcli.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class CliConfig {
  private Chains chains = new Chains();
  private WalletPreferences wallet = new WalletPreferences();

  public Chains getChains() {
    return chains;
  }

  public void setChains(Chains chains) {
    this.chains = chains;
  }

  public WalletPreferences getWallet() {
    return wallet;
  }

  public void setWallet(WalletPreferences wallet) {
    this.wallet = wallet;
  }

  public static class Chains {
    private ChainProfile mainnet;
    private ChainProfile testnet;
    private Map<String, ChainProfile> custom = new LinkedHashMap<>();

    public ChainProfile getMainnet() {
      return mainnet;
    }

    public void setMainnet(ChainProfile mainnet) {
      this.mainnet = mainnet;
    }

    public ChainProfile getTestnet() {
      return testnet;
    }

    public void setTestnet(ChainProfile testnet) {
      this.testnet = testnet;
    }

    public Map<String, ChainProfile> getCustom() {
      return custom;
    }

    public void setCustom(Map<String, ChainProfile> custom) {
      this.custom = custom;
    }
  }

  public static class WalletPreferences {
    private boolean cachePasswordInMemory = false;

    public boolean isCachePasswordInMemory() {
      return cachePasswordInMemory;
    }

    public void setCachePasswordInMemory(boolean cachePasswordInMemory) {
      this.cachePasswordInMemory = cachePasswordInMemory;
    }
  }
}
