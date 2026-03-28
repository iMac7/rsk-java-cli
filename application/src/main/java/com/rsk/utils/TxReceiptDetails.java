package com.rsk.utils;

public record TxReceiptDetails(
    String txHash,
    String blockHash,
    String blockNumber,
    String gasUsed,
    String status,
    String from,
    String to) {}
