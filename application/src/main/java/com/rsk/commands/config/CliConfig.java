package com.rsk.commands.config;

import com.rsk.utils.Chain.ChainProfile;
import java.util.LinkedHashMap;
import java.util.Map;

public class CliConfig {
  private Chains chains = new Chains();
  private NetworkPreferences network = new NetworkPreferences();
  private GasPreferences gas = new GasPreferences();
  private DisplayPreferences display = new DisplayPreferences();
  private WalletPreferences wallet = new WalletPreferences();
  private ApiKeys apiKeys = new ApiKeys();

  public Chains getChains() {
    return chains;
  }

  public void setChains(Chains chains) {
    this.chains = chains;
  }

  public NetworkPreferences getNetwork() {
    return network;
  }

  public void setNetwork(NetworkPreferences network) {
    this.network = network;
  }

  public GasPreferences getGas() {
    return gas;
  }

  public void setGas(GasPreferences gas) {
    this.gas = gas;
  }

  public DisplayPreferences getDisplay() {
    return display;
  }

  public void setDisplay(DisplayPreferences display) {
    this.display = display;
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
    private String defaultWalletName = "";

    public String getDefaultWalletName() {
      return defaultWalletName;
    }

    public void setDefaultWalletName(String defaultWalletName) {
      this.defaultWalletName = defaultWalletName;
    }
  }

  public static class NetworkPreferences {
    private String defaultNetwork = "testnet";

    public String getDefaultNetwork() {
      return defaultNetwork;
    }

    public void setDefaultNetwork(String defaultNetwork) {
      this.defaultNetwork = defaultNetwork;
    }
  }

  public static class GasPreferences {
    private long defaultGasLimit = 21_000L;
    private long defaultGasPriceGwei = 0L;

    public long getDefaultGasLimit() {
      return defaultGasLimit;
    }

    public void setDefaultGasLimit(long defaultGasLimit) {
      this.defaultGasLimit = defaultGasLimit;
    }

    public long getDefaultGasPriceGwei() {
      return defaultGasPriceGwei;
    }

    public void setDefaultGasPriceGwei(long defaultGasPriceGwei) {
      this.defaultGasPriceGwei = defaultGasPriceGwei;
    }
  }

  public static class DisplayPreferences {
    private boolean showExplorerLinks = true;
    private boolean showGasDetails = true;
    private boolean showBlockDetails = true;
    private boolean compactMode = false;

    public boolean isShowExplorerLinks() {
      return showExplorerLinks;
    }

    public void setShowExplorerLinks(boolean showExplorerLinks) {
      this.showExplorerLinks = showExplorerLinks;
    }

    public boolean isShowGasDetails() {
      return showGasDetails;
    }

    public void setShowGasDetails(boolean showGasDetails) {
      this.showGasDetails = showGasDetails;
    }

    public boolean isShowBlockDetails() {
      return showBlockDetails;
    }

    public void setShowBlockDetails(boolean showBlockDetails) {
      this.showBlockDetails = showBlockDetails;
    }

    public boolean isCompactMode() {
      return compactMode;
    }

    public void setCompactMode(boolean compactMode) {
      this.compactMode = compactMode;
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
