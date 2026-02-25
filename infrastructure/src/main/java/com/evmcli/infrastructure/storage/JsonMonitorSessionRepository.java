package com.evmcli.infrastructure.storage;

import com.evmcli.domain.model.MonitorSession;
import com.evmcli.domain.port.MonitorSessionPort;
import com.evmcli.infrastructure.json.ObjectMapperFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JsonMonitorSessionRepository implements MonitorSessionPort {
  private final Path sessionsPath;
  private final ObjectMapper objectMapper;

  public JsonMonitorSessionRepository(Path homeDir) {
    this(homeDir.resolve("sessions.json"), ObjectMapperFactory.create());
  }

  JsonMonitorSessionRepository(Path sessionsPath, ObjectMapper objectMapper) {
    this.sessionsPath = sessionsPath;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<MonitorSession> list() {
    try {
      if (!Files.exists(sessionsPath)) {
        return List.of();
      }
      return objectMapper.readValue(sessionsPath.toFile(), new TypeReference<>() {});
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to load sessions", ex);
    }
  }

  @Override
  public MonitorSession save(MonitorSession session) {
    List<MonitorSession> sessions = new ArrayList<>(list());
    sessions.removeIf(existing -> existing.getId().equals(session.getId()));
    sessions.add(session);
    writeAll(sessions);
    return session;
  }

  @Override
  public Optional<MonitorSession> find(UUID id) {
    return list().stream().filter(session -> session.getId().equals(id)).findFirst();
  }

  private void writeAll(List<MonitorSession> sessions) {
    try {
      Files.createDirectories(sessionsPath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(sessionsPath.toFile(), sessions);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to save sessions", ex);
    }
  }
}
