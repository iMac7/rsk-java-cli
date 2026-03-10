package com.rsk.commands.transfer;

import com.rsk.commands.config.CliConfig;
import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.commands.wallet.Helpers.WalletUnlockPort;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Contract;
import com.rsk.utils.Rpc;
import com.rsk.utils.Storage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

public class Helpers {
  private static final BigDecimal WEI = new BigDecimal("1000000000000000000");
  private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

  private final com.rsk.commands.config.Helpers configHelpers;
  private final com.rsk.commands.wallet.Helpers walletHelpers;
  private final WalletUnlockPort walletUnlockPort;
  private final Rpc.RpcPort rpcPort;
  private final com.rsk.commands.contract.Helpers contractHelpers;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers,
      WalletUnlockPort walletUnlockPort,
      Rpc.RpcPort rpcPort,
      com.rsk.commands.contract.Helpers contractHelpers) {
    this.configHelpers = configHelpers;
    this.walletHelpers = walletHelpers;
    this.walletUnlockPort = walletUnlockPort;
    this.rpcPort = rpcPort;
    this.contractHelpers = contractHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    Storage.JsonWalletRepository walletRepository = new Storage.JsonWalletRepository(homeDir);
    return new Helpers(
        new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)),
        com.rsk.commands.wallet.Helpers.defaultHelpers(),
        walletRepository,
        new Rpc.Web3jRpcGateway(),
        com.rsk.commands.contract.Helpers.defaultHelpers());
  }

  public ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile("custom-url", chainUrl, 0L, "NATIVE", "", "", ChainFeatures.defaults());
    }

    String chainOption = normalizeChainOption(chain);
    boolean useMainnet = mainnet;
    boolean useTestnet = testnet;
    if ("mainnet".equals(chainOption)) {
      useMainnet = true;
      useTestnet = false;
      chainOption = null;
    } else if ("testnet".equals(chainOption)) {
      useMainnet = false;
      useTestnet = true;
      chainOption = null;
    }

    CliConfig config = configHelpers.loadConfig();
    return Chain.resolve(config, new Chain.ChainSelection(useMainnet, useTestnet, chainOption));
  }

  public String resolveWalletName(String walletName) {
    if (walletName != null && !walletName.isBlank()) {
      return walletName;
    }
    return walletHelpers
        .activeWallet()
        .map(WalletMetadata::name)
        .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
  }

  public String walletAddress(String walletName) {
    return walletHelpers.requireWallet(walletName).address();
  }

  public BigDecimal nativeBalance(ChainProfile chainProfile, String address) {
    return new BigDecimal(rpcPort.getNativeBalance(chainProfile, address)).divide(WEI);
  }

  public BigInteger toWei(BigDecimal amount) {
    return amount.multiply(WEI).toBigIntegerExact();
  }

  public BigInteger defaultGasLimit() {
    return BigInteger.valueOf(configHelpers.loadConfig().getGas().getDefaultGasLimit());
  }

  public BigInteger defaultGasPriceWei(ChainProfile chainProfile) {
    long gasPriceGwei = configHelpers.loadConfig().getGas().getDefaultGasPriceGwei();
    if (gasPriceGwei <= 0L) {
      return fetchGasPriceWei(chainProfile);
    }
    return BigInteger.valueOf(gasPriceGwei).multiply(GWEI);
  }

  public BigInteger gasPriceRbtcToWei(BigDecimal gasPriceRbtc) {
    return gasPriceRbtc.multiply(WEI).toBigIntegerExact();
  }

  public BigInteger fetchGasPriceWei(ChainProfile chainProfile) {
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
      return web3j.ethGasPrice().send().getGasPrice();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch gas price", ex);
    }
  }

  public PendingTransfer sendNative(
      ChainProfile chainProfile,
      String walletName,
      char[] password,
      String to,
      BigInteger valueWei,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    String privateKeyHex = walletUnlockPort.unlockPrivateKeyHex(walletName, password);
    try {
      com.rsk.utils.Transaction.PendingTransaction pending =
          com.rsk.utils.Transaction.submit(
              chainProfile,
              privateKeyHex,
              new com.rsk.utils.Transaction.SendRequest(to, valueWei, gasLimit, gasPriceWei, data));
      return new PendingTransfer(pending.fromAddress(), pending.txHash());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to send transfer", ex);
    }
  }

  public PendingTransfer sendToken(
      ChainProfile chainProfile,
      String walletName,
      char[] password,
      String tokenAddress,
      String to,
      BigDecimal value,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    String privateKeyHex = walletUnlockPort.unlockPrivateKeyHex(walletName, password);

    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
      TokenMetadata metadata = readTokenMetadata(chainProfile, tokenAddress);
      BigInteger amountUnits = value.movePointRight(metadata.decimals()).toBigIntegerExact();
      Function function =
          new Function(
              "transfer",
              List.of(new Address(to), new Uint256(amountUnits)),
              List.of());
      String encodedData = data != null && !data.isBlank() ? data : FunctionEncoder.encode(function);

      BigInteger resolvedGasLimit = gasLimit;
      if (resolvedGasLimit == null) {
        String fromAddress = walletAddress(walletName);
        EthEstimateGas estimate =
            web3j
                .ethEstimateGas(
                    Transaction.createFunctionCallTransaction(
                        fromAddress,
                        null,
                        gasPriceWei,
                        null,
                        tokenAddress,
                        BigInteger.ZERO,
                        encodedData))
                .send();
        if (estimate.hasError() || estimate.getAmountUsed() == null) {
          resolvedGasLimit = BigInteger.valueOf(100_000L);
        } else {
          resolvedGasLimit = estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
        }
      }
      com.rsk.utils.Transaction.PendingTransaction pending =
          com.rsk.utils.Transaction.submit(
              chainProfile,
              privateKeyHex,
              new com.rsk.utils.Transaction.SendRequest(
                  tokenAddress, BigInteger.ZERO, resolvedGasLimit, gasPriceWei, encodedData));
      return new PendingTransfer(pending.fromAddress(), pending.txHash());
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Invalid token amount for token decimals.", ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to send token transfer", ex);
    }
  }

  public TokenMetadata readTokenMetadata(ChainProfile chainProfile, String tokenAddress) {
    try {
      List<Type> symbolOut =
          contractHelpers.executeReadFunction(
              chainProfile,
              tokenAddress,
              "symbol",
              List.of(),
              List.of(TypeReference.create(Utf8String.class)));
      List<Type> decimalsOut =
          contractHelpers.executeReadFunction(
              chainProfile,
              tokenAddress,
              "decimals",
              List.of(),
              List.of(TypeReference.create(Uint8.class)));
      if (symbolOut.isEmpty() || decimalsOut.isEmpty()) {
        throw new IllegalArgumentException("Invalid token contract address");
      }
      return new TokenMetadata(
          ((Utf8String) symbolOut.get(0)).getValue(),
          ((Uint8) decimalsOut.get(0)).getValue().intValue());
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid token contract address", ex);
    }
  }

  public String resolveTokenAddress(ChainProfile chainProfile, String tokenOption) {
    if (tokenOption == null || tokenOption.isBlank()) {
      return null;
    }
    String trimmed = tokenOption.trim();
    if ("rbtc".equalsIgnoreCase(trimmed)) {
      return null;
    }
    if (Contract.hasToken(trimmed)) {
      return Contract.tokenAddress(trimmed, chainProfile);
    }
    if (!trimmed.matches("^0x[a-fA-F0-9]{40}$")) {
      throw new IllegalArgumentException("Invalid token contract address");
    }
    return trimmed;
  }

  public String networkDisplayName(ChainProfile chainProfile) {
    if (chainProfile.chainId() == 30L) {
      return "Rootstock Mainnet";
    }
    if (chainProfile.chainId() == 31L) {
      return "Rootstock Testnet";
    }
    return chainProfile.name();
  }

  public record TransferRequest(String recipient, BigDecimal amount) {}

  public TransferResult waitForConfirmation(ChainProfile chainProfile, PendingTransfer pendingTransfer) {
    try {
      TransactionReceipt receipt =
          com.rsk.utils.Transaction.waitForReceipt(chainProfile, pendingTransfer.txHash(), 120, 2000L);
      if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
        throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
      }
      return new TransferResult(pendingTransfer.walletAddress(), pendingTransfer.txHash(), receipt);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to confirm transfer", ex);
    }
  }

  public record PendingTransfer(String walletAddress, String txHash) {}

  public record TransferResult(String walletAddress, String txHash, TransactionReceipt receipt) {}

  public record TokenMetadata(String symbol, int decimals) {}
  private static String normalizeChainOption(String chainOption) {
    if (chainOption == null || chainOption.isBlank()) {
      return chainOption;
    }
    String normalized = chainOption.trim();
    if (normalized.startsWith("chains.custom.")) {
      return normalized.substring("chains.custom.".length());
    }
    if ("chains.mainnet".equals(normalized)) {
      return "mainnet";
    }
    if ("chains.testnet".equals(normalized)) {
      return "testnet";
    }
    return normalized;
  }
}
