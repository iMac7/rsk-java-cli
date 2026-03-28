package com.rsk.commands.tx;

import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Transaction;
import com.rsk.utils.TxReceiptDetails;
import java.math.BigInteger;
import java.util.Optional;

public class Helpers {
  public static Helpers defaultHelpers() {
    return new Helpers();
  }

  public Optional<String> receiptStatus(ChainProfile chainProfile, String txHash) {
    return receiptDetails(chainProfile, txHash).map(TxReceiptDetails::status);
  }

  public Optional<TxReceiptDetails> receiptDetails(ChainProfile chainProfile, String txHash) {
    return Transaction.receiptDetails(chainProfile, txHash);
  }

  public BigInteger currentBlockNumber(ChainProfile chainProfile) {
    return Transaction.currentBlockNumber(chainProfile);
  }
}
