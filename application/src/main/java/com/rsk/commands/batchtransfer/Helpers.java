package com.rsk.commands.batchtransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.commands.transfer.Helpers.PendingTransfer;
import com.rsk.commands.transfer.Helpers.TransferRequest;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Json;
import com.rsk.utils.Transaction;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class Helpers {
  private static final ObjectMapper OBJECT_MAPPER = Json.ObjectMapperFactory.create();
  private static final TypeReference<List<BatchFileEntry>> BATCH_FILE_TYPE = new TypeReference<>() {};

  private final com.rsk.commands.transfer.Helpers transferHelpers;
  private final com.rsk.commands.balance.Helpers balanceHelpers;

  public Helpers(
      com.rsk.commands.transfer.Helpers transferHelpers,
      com.rsk.commands.balance.Helpers balanceHelpers) {
    this.transferHelpers = transferHelpers;
    this.balanceHelpers = balanceHelpers;
  }

  public static Helpers defaultHelpers() {
    return new Helpers(
        com.rsk.commands.transfer.Helpers.defaultHelpers(),
        com.rsk.commands.balance.Helpers.defaultHelpers());
  }

  public ChainProfile resolveChain(boolean testnet) {
    return transferHelpers.resolveChain(false, testnet, null, null);
  }

  public String resolveWalletName() {
    return transferHelpers.resolveWalletName(null);
  }

  public String walletAddress(String walletName) {
    return transferHelpers.walletAddress(walletName);
  }

  public BigDecimal nativeBalance(ChainProfile chainProfile, String walletAddress) {
    return transferHelpers.nativeBalance(chainProfile, walletAddress);
  }

  public String unlockWalletPrivateKeyHex(String walletName, char[] password) {
    return transferHelpers.unlockWalletPrivateKeyHex(walletName, password);
  }

  public TransferDefaults transferDefaults(ChainProfile chainProfile) {
    return new TransferDefaults(
        Transaction.defaultGasLimit(), Transaction.defaultGasPriceWei(chainProfile));
  }

  public List<TransferRequest> loadRequestsFromFile(ChainProfile chainProfile, Path filePath) {
    if (filePath == null) {
      throw new IllegalArgumentException("Provide --interactive or --file <path>.");
    }
    if (!Files.exists(filePath)) {
      throw new IllegalArgumentException("Batch file not found: " + filePath);
    }
    try {
      List<BatchFileEntry> entries = OBJECT_MAPPER.readValue(filePath.toFile(), BATCH_FILE_TYPE);
      if (entries == null || entries.isEmpty()) {
        throw new IllegalArgumentException("Batch file is empty.");
      }
      List<TransferRequest> requests = new ArrayList<>();
      for (BatchFileEntry entry : entries) {
        if (entry == null || entry.to() == null || entry.to().isBlank()) {
          throw new IllegalArgumentException("Each batch item must include a non-empty 'to' value.");
        }
        if (entry.value() == null || entry.value().compareTo(BigDecimal.ZERO) <= 0) {
          throw new IllegalArgumentException("Each batch item must include a positive 'value'.");
        }
        requests.add(
            new TransferRequest(resolveRecipient(chainProfile, entry.to()), entry.value()));
      }
      return requests;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Unable to read batch file: " + filePath, ex);
    }
  }

  public String resolveRecipient(ChainProfile chainProfile, String target) {
    return balanceHelpers.resolveAddressInput(chainProfile, target);
  }

  public PendingTransfer submitTransfer(
      ChainProfile chainProfile,
      String privateKeyHex,
      TransferRequest request,
      TransferDefaults defaults) {
    return transferHelpers.sendNativeWithPrivateKey(
        chainProfile,
        privateKeyHex,
        request.recipient(),
        Transaction.toWei(request.amount()),
        defaults.gasLimit(),
        defaults.gasPriceWei(),
        "");
  }

  public TransactionReceipt waitForConfirmation(ChainProfile chainProfile, PendingTransfer pendingTransfer) {
    return Transaction.waitForSuccessfulReceipt(chainProfile, pendingTransfer.txHash(), 120, 2000L);
  }

  public String rootMessage(Throwable ex) {
    Throwable current = ex;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? ex.getMessage() : current.getMessage();
  }

  private record BatchFileEntry(String to, BigDecimal value) {}

  public record TransferDefaults(BigInteger gasLimit, BigInteger gasPriceWei) {}
}
