package com.rsk.commands.tx;

import static com.rsk.utils.Terminal.*;

import com.rsk.java_cli.CliHelpers;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Loader;
import com.rsk.utils.Rpc.TxReceiptDetails;
import java.math.BigInteger;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public class Subcommands {
  private Subcommands() {}

  @Command(
      name = "tx",
      description = "@|bold,fg(214)Check the status of a transaction|@",
      mixinStandardHelpOptions = true)
  public static class TxCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    @Option(
        names = {"-id", "--txid"},
        required = true,
        paramLabel = "<txid>",
        description = "@|fg(220)Transaction ID|@")
    String txid;

    @Option(
        names = "--monitor",
        description = "@|fg(220)Keep monitoring the transaction until confirmation|@")
    boolean monitor;

    @Option(
        names = "--confirmations",
        defaultValue = "12",
        description = "@|fg(220)Required confirmations for monitoring|@ (default: ${DEFAULT-VALUE})")
    int confirmations;

    @Option(names = "--mainnet", description = "@|fg(220)Check the transaction on the mainnet|@")
    boolean mainnet;

    @Option(
        names = {"-t", "--testnet"},
        description = "@|fg(220)Check the transaction status on the testnet|@")
    boolean testnet;

    @Option(
        names = "--chain",
        paramLabel = "<name>",
        description = "@|fg(220)Use config chain key, e.g. chains.custom.<name> or <name>|@")
    String chain;

    @Option(
        names = "--chainurl",
        paramLabel = "<url>",
        description = "@|fg(220)Use an explicit RPC URL|@")
    String chainUrl;

    @Override
    public Integer call() {
      ChainProfile chainProfile =
          configHelpers().resolveChain(mainnet, testnet, chain, chainUrl);
      TxReceiptDetails details =
          helpers()
              .receiptDetails(chainProfile, txid)
              .orElseThrow(() -> new IllegalStateException("Transaction receipt not found yet."));
      printReceipt(details);
      if (monitor) {
        monitorTransaction(chainProfile, details);
      }
      return 0;
    }

    private void printReceipt(TxReceiptDetails details) {
      System.out.println();
      System.out.println(cEmph("Transaction Details"));
      System.out.println(cInfo("🔑 Tx ID: ") + details.txHash());
      System.out.println(cInfo("🔗 Block Hash: ") + safe(details.blockHash()));
      System.out.println(cInfo("🧱 Block No.: ") + safe(details.blockNumber()));
      System.out.println(cInfo("⛽ Gas Used: ") + safe(details.gasUsed()));
      System.out.println(cInfo("✅ Status: ") + formatStatus(details.status()));
      System.out.println(cInfo("📤 From: ") + safe(details.from()));
      System.out.println(cInfo("📥 To: ") + safe(details.to()));
    }

    private String safe(String value) {
      return value == null || value.isBlank() ? "(not available)" : value;
    }

    private String formatStatus(String status) {
      if ("0x1".equalsIgnoreCase(status)) {
        return cOk("Success");
      }
      if ("0x0".equalsIgnoreCase(status)) {
        return cError("Failed");
      }
      return safe(status);
    }

    private void monitorTransaction(ChainProfile chainProfile, TxReceiptDetails details) {
      System.out.println();
      System.out.println(cEmph("Starting Transaction Monitoring"));
      System.out.println(cInfo("Network: ") + chainProfile.name());
      System.out.println(cInfo("Transaction: ") + txid);
      System.out.println(cInfo("Required confirmations: ") + confirmations);
      System.out.println();
      System.out.println(cOk("✔ Monitor initialized successfully"));

      Loader.runWithSpinner("Starting transaction monitoring...", () -> {
        Thread.sleep(900L);
        return null;
      });

      System.out.println(cOk("✅ Started monitoring transaction: ") + txid);
      System.out.println(cOk("✔ Transaction monitoring started successfully"));
      System.out.println();
      System.out.println(cEmph("Monitoring started successfully!"));
      System.out.println(cMuted("Press Ctrl+C to stop monitoring"));
      System.out.println();

      long checkCount = 0L;
      try {
        while (true) {
          TxReceiptDetails currentDetails =
              helpers()
                  .receiptDetails(chainProfile, txid)
                  .orElseThrow(() -> new IllegalStateException("Transaction receipt not found yet."));
          if (currentDetails.blockNumber() == null || currentDetails.blockNumber().isBlank()) {
            System.out.println(
                cInfo("📊 TX ")
                    + abbreviate(txid)
                    + cInfo(" - Status: ")
                    + "pending"
                    + cInfo(", Confirmations: ")
                    + "(not available yet)");
            checkCount++;
            waitForNextConfirmationCheck();
            continue;
          }
          BigInteger receiptBlock = new BigInteger(currentDetails.blockNumber());
          BigInteger currentBlock = helpers().currentBlockNumber(chainProfile);
          long confirmationsCount = currentBlock.subtract(receiptBlock).add(BigInteger.ONE).max(BigInteger.ZERO).longValue();
          System.out.println(
              cInfo("📊 TX ")
                  + abbreviate(txid)
                  + cInfo(" - Status: ")
                  + "confirmed"
                  + cInfo(", Confirmations: ")
                  + confirmationsCount);
          checkCount++;

          if (confirmationsCount >= confirmations) {
            System.out.println(
                cOk("✅ Transaction ")
                    + abbreviate(txid)
                    + cOk(" confirmed with ")
                    + confirmationsCount
                    + cOk(" confirmations"));
            System.out.println(cWarn("⚠️  Monitoring stopped after ") + checkCount + cWarn(" checks"));
            return;
          }

          waitForNextConfirmationCheck();
        }
      } catch (RuntimeException ex) {
        throw ex;
      }
    }

    private void waitForNextConfirmationCheck() {
      Loader.runWithSpinner(
          "Waiting for next confirmation check...",
          () -> {
            Thread.sleep(3000L);
            return null;
          });
    }

    private String abbreviate(String hash) {
      return hash.length() <= 12 ? hash : hash.substring(0, 10) + "...";
    }

    private Helpers helpers() {
      return CliHelpers.deps(spec).txHelpers();
    }

    private com.rsk.commands.config.Helpers configHelpers() {
      return CliHelpers.deps(spec).configHelpers();
    }
  }

}
