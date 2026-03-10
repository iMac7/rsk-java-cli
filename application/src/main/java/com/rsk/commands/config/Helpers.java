package com.rsk.commands.config;

import com.rsk.utils.Storage.JsonConfigRepository;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public class Helpers {
  private final ConfigPort configPort;

  public Helpers(ConfigPort configPort) {
    this.configPort = configPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new JsonConfigRepository(homeDir));
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
                "https://mainnet.rpc.rsk.co",
                30,
                "RBTC",
                "https://explorer.rsk.co/tx/%s",
                "https://explorer.rsk.co/address/%s",
                new ChainFeatures(true, true, true, true)));
    config
        .getChains()
        .setTestnet(
            new ChainProfile(
                "testnet",
                "https://public-node.testnet.rsk.co",
                31,
                "tRBTC",
                "https://explorer.testnet.rsk.co/tx/%s",
                "https://explorer.testnet.rsk.co/address/%s",
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
