package com.evmcli.domain.model;

import java.time.Instant;
import java.util.UUID;

public class MonitorSession {
  public enum Type {
    ADDRESS_BALANCE,
    ADDRESS_TRANSACTIONS,
    TX_CONFIRMATIONS
  }

  public enum Status {
    ACTIVE,
    STOPPED,
    STALE
  }

  private UUID id;
  private String chainRef;
  private Type type;
  private String target;
  private Instant createdAt;
  private Status status;
  private int pollIntervalSeconds;
  private int confirmationsRequired;
  private long checkCount;
  private Instant lastCheckedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getChainRef() {
    return chainRef;
  }

  public void setChainRef(String chainRef) {
    this.chainRef = chainRef;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public int getPollIntervalSeconds() {
    return pollIntervalSeconds;
  }

  public void setPollIntervalSeconds(int pollIntervalSeconds) {
    this.pollIntervalSeconds = pollIntervalSeconds;
  }

  public int getConfirmationsRequired() {
    return confirmationsRequired;
  }

  public void setConfirmationsRequired(int confirmationsRequired) {
    this.confirmationsRequired = confirmationsRequired;
  }

  public long getCheckCount() {
    return checkCount;
  }

  public void setCheckCount(long checkCount) {
    this.checkCount = checkCount;
  }

  public Instant getLastCheckedAt() {
    return lastCheckedAt;
  }

  public void setLastCheckedAt(Instant lastCheckedAt) {
    this.lastCheckedAt = lastCheckedAt;
  }
}
