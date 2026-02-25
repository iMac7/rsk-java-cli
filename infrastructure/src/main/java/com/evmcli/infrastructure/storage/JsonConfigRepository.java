package com.evmcli.infrastructure.storage;

import com.evmcli.domain.model.ChainFeatures;
import com.evmcli.domain.model.ChainProfile;
import com.evmcli.domain.model.CliConfig;
import com.evmcli.domain.port.ConfigPort;
import com.evmcli.infrastructure.json.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonConfigRepository implements ConfigPort {
  private final Path configPath;
  private final ObjectMapper objectMapper;

  public JsonConfigRepository(Path homeDir) {
    this(homeDir.resolve("config.json"), ObjectMapperFactory.create());
  }

  JsonConfigRepository(Path configPath, ObjectMapper objectMapper) {
    this.configPath = configPath;
    this.objectMapper = objectMapper;
  }

  @Override
  public CliConfig load() {
    try {
      if (!Files.exists(configPath)) {
        CliConfig defaultConfig = defaultConfig();
        save(defaultConfig);
        return defaultConfig;
      }
      return objectMapper.readValue(configPath.toFile(), CliConfig.class);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load config", ex);
    }
  }

  @Override
  public void save(CliConfig config) {
    try {
      Files.createDirectories(configPath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to save config", ex);
    }
  }

  private static CliConfig defaultConfig() {
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
    return config;
  }
}
