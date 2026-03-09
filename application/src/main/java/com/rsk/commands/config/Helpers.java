package com.rsk.commands.config;

import com.rsk.utils.Storage.JsonConfigRepository;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public class Helpers {
  private final ConfigPort configPort;

  public Helpers(ConfigPort configPort) {
    this.configPort = configPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".evm-cli");
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

  public static void ensureConfigShape(CliConfig config) {
    if (config.getChains() == null) {
      config.setChains(new CliConfig.Chains());
    }
    if (config.getChains().getCustom() == null) {
      config.getChains().setCustom(new LinkedHashMap<>());
    }
    if (config.getWallet() == null) {
      config.setWallet(new CliConfig.WalletPreferences());
    }
    if (config.getApiKeys() == null) {
      config.setApiKeys(new CliConfig.ApiKeys());
    }
  }
}
