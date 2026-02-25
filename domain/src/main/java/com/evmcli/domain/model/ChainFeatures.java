package com.evmcli.domain.model;

public record ChainFeatures(
    boolean supportsNameResolution,
    boolean supportsBridge,
    boolean supportsContractVerification,
    boolean supportsHistoryApi) {

  public static ChainFeatures defaults() {
    return new ChainFeatures(false, false, false, false);
  }
}
