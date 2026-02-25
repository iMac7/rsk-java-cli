package com.evmcli.domain.port;

import com.evmcli.domain.model.CliConfig;

public interface ConfigPort {
  CliConfig load();

  void save(CliConfig config);
}
