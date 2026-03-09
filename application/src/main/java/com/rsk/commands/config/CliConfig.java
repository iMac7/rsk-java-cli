package com.rsk.commands.config;

import com.rsk.utils.Chain.ChainProfile;
import java.util.LinkedHashMap;
import java.util.Map;

public class CliConfig {
  private Chains chains = new Chains();
  private WalletPreferences wallet = new WalletPreferences();
  private ApiKeys apiKeys = new ApiKeys();

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

  public ApiKeys getApiKeys() {
    return apiKeys;
  }

  public void setApiKeys(ApiKeys apiKeys) {
    this.apiKeys = apiKeys;
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

  public static class ApiKeys {
    private String alchemyApiKey = "";

    public String getAlchemyApiKey() {
      return alchemyApiKey;
    }

    public void setAlchemyApiKey(String alchemyApiKey) {
      this.alchemyApiKey = alchemyApiKey;
    }
  }
}
