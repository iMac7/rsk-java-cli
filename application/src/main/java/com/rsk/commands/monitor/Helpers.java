package com.rsk.commands.monitor;

import com.rsk.utils.Monitorsession;
import com.rsk.utils.Storage;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class Helpers {
  private final Monitorsession.MonitorSessionPort sessionPort;
  private final ExecutorService executorService;

  public Helpers(Monitorsession.MonitorSessionPort sessionPort) {
    this.sessionPort = sessionPort;
    this.executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new Storage.JsonMonitorSessionRepository(homeDir));
  }

  public List<Monitorsession.MonitorSession> listSessions() {
    return sessionPort.list();
  }

  public Optional<Monitorsession.MonitorSession> findSession(UUID id) {
    return sessionPort.find(id);
  }

  public Monitorsession.MonitorSession startTxConfirmations(
      String chainRef, String txHash, int pollIntervalSeconds, int confirmationsRequired) {
    Monitorsession.MonitorSession session = new Monitorsession.MonitorSession();
    session.setId(UUID.randomUUID());
    session.setChainRef(chainRef);
    session.setType(Monitorsession.MonitorSession.Type.TX_CONFIRMATIONS);
    session.setTarget(txHash);
    session.setCreatedAt(Instant.now());
    session.setStatus(Monitorsession.MonitorSession.Status.ACTIVE);
    session.setPollIntervalSeconds(pollIntervalSeconds);
    session.setConfirmationsRequired(confirmationsRequired);
    session.setCheckCount(0);
    sessionPort.save(session);

    executorService.submit(
        () -> {
          try {
            while (true) {
              Monitorsession.MonitorSession current =
                  sessionPort.find(session.getId()).orElse(session);
              if (current.getStatus() != Monitorsession.MonitorSession.Status.ACTIVE) {
                break;
              }
              current.setCheckCount(current.getCheckCount() + 1);
              current.setLastCheckedAt(Instant.now());
              sessionPort.save(current);
              Thread.sleep(Math.max(1, pollIntervalSeconds) * 1000L);
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        });

    return session;
  }

  public void stopSession(UUID id) {
    sessionPort.find(id)
        .ifPresent(
            session -> {
              session.setStatus(Monitorsession.MonitorSession.Status.STOPPED);
              sessionPort.save(session);
            });
  }
}
