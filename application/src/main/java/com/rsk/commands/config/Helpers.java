package com.rsk.commands.config;

import com.rsk.utils.Chain;
import com.rsk.utils.Storage.JsonConfigRepository;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Constants;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public class Helpers {
  public interface ConfigPort {
    CliConfig load();

    void save(CliConfig config);
  }

  private final ConfigPort configPort;

  public Helpers(ConfigPort configPort) {
    this.configPort = configPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new JsonConfigRepository(homeDir));
  }

  public ChainProfile resolveChain(boolean mainnet, boolean testnet, String chain, String chainUrl) {
    return Chain.resolveChain(loadConfig(), mainnet, testnet, chain, chainUrl);
  }

  public abstract static class ChainResolutionSupport {
    protected final Helpers configHelpers;

    protected ChainResolutionSupport(Helpers configHelpers) {
      this.configHelpers = configHelpers;
    }

    public final ChainProfile resolveChain(
        boolean mainnet, boolean testnet, String chain, String chainUrl) {
      return configHelpers.resolveChain(mainnet, testnet, chain, chainUrl);
    }
  }

  public CliConfig loadConfig() {
    CliConfig config = configPort.load();
    ensureConfigShape(config);
    return config;
  }

  public void saveConfig(CliConfig config) {
    ensureConfigShape(config);
    configPort.save(config);
  }

  public static CliConfig defaultConfig() {
    CliConfig config = new CliConfig();
    config
        .getChains()
        .setMainnet(
            new ChainProfile(
                "mainnet",
                Constants.RSK_MAINNET_RPC_URL,
                30,
                "RBTC",
                Constants.RSK_MAINNET_EXPLORER_URL + "/tx/%s",
                Constants.RSK_MAINNET_EXPLORER_URL + "/address/%s",
                new ChainFeatures(true, true, true, true)));
    config
        .getChains()
        .setTestnet(
            new ChainProfile(
                "testnet",
                Constants.RSK_TESTNET_RPC_URL,
                31,
                "tRBTC",
                Constants.RSK_TESTNET_EXPLORER_URL + "/tx/%s",
                Constants.RSK_TESTNET_EXPLORER_URL + "/address/%s",
                new ChainFeatures(true, false, false, true)));
    ensureConfigShape(config);
    return config;
  }

  public static void ensureConfigShape(CliConfig config) {
    if (config.getChains() == null) {
      config.setChains(new CliConfig.Chains());
    }
    if (config.getChains().getCustom() == null) {
      config.getChains().setCustom(new LinkedHashMap<>());
    }
    if (config.getNetwork() == null) {
      config.setNetwork(new CliConfig.NetworkPreferences());
    }
    if (config.getNetwork().getDefaultNetwork() == null
        || config.getNetwork().getDefaultNetwork().isBlank()) {
      config.getNetwork().setDefaultNetwork("testnet");
    }
    if (config.getGas() == null) {
      config.setGas(new CliConfig.GasPreferences());
    }
    if (config.getGas().getDefaultGasLimit() <= 0) {
      config.getGas().setDefaultGasLimit(21_000L);
    }
    if (config.getGas().getDefaultGasPriceGwei() < 0) {
      config.getGas().setDefaultGasPriceGwei(0L);
    }
    if (config.getDisplay() == null) {
      config.setDisplay(new CliConfig.DisplayPreferences());
    }
    if (config.getWallet() == null) {
      config.setWallet(new CliConfig.WalletPreferences());
    }
    if (config.getWallet().getDefaultWalletName() == null) {
      config.getWallet().setDefaultWalletName("");
    }
    if (config.getApiKeys() == null) {
      config.setApiKeys(new CliConfig.ApiKeys());
    }
    if (config.getApiKeys().getAlchemyApiKey() == null) {
      config.getApiKeys().setAlchemyApiKey("");
    }
  }
}
