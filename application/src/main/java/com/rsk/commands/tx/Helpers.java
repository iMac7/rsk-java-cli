package com.rsk.commands.tx;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Monitorsession;
import com.rsk.utils.Rpc;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class Helpers {
  private final Rpc.RpcPort rpcPort;
  private final Monitorsession.MonitorSessionPort sessionPort;
  private final ExecutorService executorService;

  public Helpers(Rpc.RpcPort rpcPort, Monitorsession.MonitorSessionPort sessionPort) {
    this.rpcPort = rpcPort;
    this.sessionPort = sessionPort;
    this.executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
  }

  public Optional<String> receiptStatus(ChainProfile chainProfile, String txHash) {
    return rpcPort.getTransactionReceiptStatus(chainProfile, txHash);
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
            while (session.getStatus() == Monitorsession.MonitorSession.Status.ACTIVE) {
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

  public void stopSession(UUID id) {
    sessionPort.find(id)
        .ifPresent(
            session -> {
              session.setStatus(Monitorsession.MonitorSession.Status.STOPPED);
              sessionPort.save(session);
            });
  }
}
