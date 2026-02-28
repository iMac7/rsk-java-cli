package com.evmcli.application;

import com.evmcli.domain.model.MonitorSession;
import com.evmcli.domain.port.MonitorSessionPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class MonitorManager {
  private final MonitorSessionPort sessionPort;
  private final ExecutorService executorService;

  public MonitorManager(MonitorSessionPort sessionPort) {
    this.sessionPort = sessionPort;
    this.executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
  }

  public List<MonitorSession> list() {
    return sessionPort.list();
  }

  public Optional<MonitorSession> find(UUID id) {
    return sessionPort.find(id);
  }

  public MonitorSession startTxConfirmations(
      String chainRef, String txHash, int pollIntervalSeconds, int confirmationsRequired) {
    MonitorSession session = new MonitorSession();
    session.setId(UUID.randomUUID());
    session.setChainRef(chainRef);
    session.setType(MonitorSession.Type.TX_CONFIRMATIONS);
    session.setTarget(txHash);
    session.setCreatedAt(Instant.now());
    session.setStatus(MonitorSession.Status.ACTIVE);
    session.setPollIntervalSeconds(pollIntervalSeconds);
    session.setConfirmationsRequired(confirmationsRequired);
    session.setCheckCount(0);
    sessionPort.save(session);

    executorService.submit(
        () -> {
          try {
            while (session.getStatus() == MonitorSession.Status.ACTIVE) {
              session.setCheckCount(session.getCheckCount() + 1);
              session.setLastCheckedAt(Instant.now());
              sessionPort.save(session);
              Thread.sleep(Math.max(1, pollIntervalSeconds) * 1000L);
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        });

    return session;
  }

  public void stop(UUID id) {
    sessionPort.find(id)
        .ifPresent(
            session -> {
              session.setStatus(MonitorSession.Status.STOPPED);
              sessionPort.save(session);
            });
  }
}
