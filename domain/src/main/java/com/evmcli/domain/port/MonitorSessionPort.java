package com.evmcli.domain.port;

import com.evmcli.domain.model.MonitorSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitorSessionPort {
  List<MonitorSession> list();

  MonitorSession save(MonitorSession session);

  Optional<MonitorSession> find(UUID id);
}
