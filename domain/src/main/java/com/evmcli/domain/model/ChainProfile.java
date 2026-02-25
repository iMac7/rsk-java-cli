package com.evmcli.domain.model;

public record ChainProfile(
    String name,
    String rpcUrl,
    long chainId,
    String nativeSymbol,
    String explorerTxUrlTemplate,
    String explorerAddressUrlTemplate,
    ChainFeatures features) {}
