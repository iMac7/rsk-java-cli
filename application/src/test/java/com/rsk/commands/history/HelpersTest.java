package com.rsk.commands.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rsk.commands.config.CliConfig;
import org.junit.jupiter.api.Test;

class HelpersTest {
  @Test
  void resolveApiKeyPrefersExplicitValue() {
    Helpers helpers = new Helpers(new com.rsk.commands.config.Helpers(new FixedConfigPort("config-key")));

    assertThat(helpers.resolveApiKey("cli-key")).isEqualTo("cli-key");
  }

  @Test
  void resolveApiKeyFallsBackToConfigWhenEnvAndExplicitAreMissing() {
    Helpers helpers = new Helpers(new com.rsk.commands.config.Helpers(new FixedConfigPort("config-key")));

    assertThat(helpers.resolveApiKey(null)).isEqualTo("config-key");
  }

  @Test
  void resolveApiKeyRejectsMissingValueWithUpdatedGuidance() {
    Helpers helpers = new Helpers(new com.rsk.commands.config.Helpers(new FixedConfigPort("")));

    assertThatThrownBy(() -> helpers.resolveApiKey(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Alchemy API key is required. Provide --apikey, set "
                + Helpers.ALCHEMY_API_KEY_ENV
                + ", or set config.apiKeys.alchemyApiKey.");
  }

  private static final class FixedConfigPort implements com.rsk.commands.config.Helpers.ConfigPort {
    private final String apiKey;

    private FixedConfigPort(String apiKey) {
      this.apiKey = apiKey;
    }

    @Override
    public CliConfig load() {
      CliConfig config = com.rsk.commands.config.Helpers.defaultConfig();
      config.getApiKeys().setAlchemyApiKey(apiKey);
      return config;
    }

    @Override
    public void save(CliConfig config) {
      throw new UnsupportedOperationException();
    }
  }
}
