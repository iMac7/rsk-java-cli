package com.evmcli.domain.model;

import java.time.Instant;
import java.util.UUID;

public record WalletMetadata(
    UUID walletId,
    String name,
    String address,
    String keystorePath,
    Instant createdAt,
    Instant lastUsedAt) {}
