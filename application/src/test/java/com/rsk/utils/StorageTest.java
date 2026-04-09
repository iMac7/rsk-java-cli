package com.rsk.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.rsk.commands.config.CliConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
  @TempDir Path tempDir;

  @Test
  void jsonConfigRepositorySavesReadableConfig() {
    Storage.JsonConfigRepository repository = new Storage.JsonConfigRepository(tempDir);
    CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();
    config.getApiKeys().setAlchemyApiKey("alchemy-key");

    repository.save(config);

    Path configPath = tempDir.resolve("config.json");
    assertThat(Files.exists(configPath)).isTrue();
    assertThat(repository.load().getApiKeys().getAlchemyApiKey()).isEqualTo("alchemy-key");
  }
}
