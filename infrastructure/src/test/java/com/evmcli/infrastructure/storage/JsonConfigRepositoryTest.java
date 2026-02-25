package com.evmcli.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.evmcli.domain.model.CliConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JsonConfigRepositoryTest {
  @Test
  void createsDefaultConfigWhenMissing() throws Exception {
    Path tempDir = Files.createTempDirectory("evmcli-config-test");
    JsonConfigRepository repository = new JsonConfigRepository(tempDir);

    CliConfig config = repository.load();

    assertThat(config.getChains().getMainnet()).isNotNull();
    assertThat(config.getChains().getTestnet()).isNotNull();
    assertThat(Files.exists(tempDir.resolve("config.json"))).isTrue();
  }
}
