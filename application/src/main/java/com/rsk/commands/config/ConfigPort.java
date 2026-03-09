package com.rsk.commands.config;

public interface ConfigPort {
  CliConfig load();

  void save(CliConfig config);
}
