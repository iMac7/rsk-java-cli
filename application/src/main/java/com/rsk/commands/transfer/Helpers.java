package com.rsk.commands.transfer;

import com.rsk.commands.config.CliConfig;
import com.rsk.commands.wallet.Helpers.WalletUnlockPort;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rpc;
import com.rsk.utils.Storage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

public class Helpers {
  private static final BigDecimal WEI = new BigDecimal("1000000000000000000");

  private final com.rsk.commands.config.Helpers configHelpers;
  private final Rpc.RpcPort rpcPort;
  private final WalletUnlockPort walletUnlockPort;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      Rpc.RpcPort rpcPort,
      WalletUnlockPort walletUnlockPort) {
    this.configHelpers = configHelpers;
    this.rpcPort = rpcPort;
    this.walletUnlockPort = walletUnlockPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    Storage.JsonWalletRepository walletRepository = new Storage.JsonWalletRepository(homeDir);
    return new Helpers(
        new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)),
        new Rpc.Web3jRpcGateway(),
        walletRepository);
  }

  public ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    if (chainUrl != null && !chainUrl.isBlank()) {
      return new ChainProfile(
          "custom-url", chainUrl, 0L, "NATIVE", "", "", ChainFeatures.defaults());
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
    return BigInteger.valueOf(gasPriceGwei).multiply(BigInteger.valueOf(1_000_000_000L));
  }

  public BigInteger fetchGasPriceWei(ChainProfile chainProfile) {
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
      return web3j.ethGasPrice().send().getGasPrice();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch gas price", ex);
    }
  }

  public String sendNative(
      ChainProfile chainProfile,
      String walletName,
      char[] password,
      String to,
      BigInteger valueWei,
      BigInteger gasLimit,
      BigInteger gasPriceWei,
      String data) {
    String privateKeyHex = walletUnlockPort.unlockPrivateKeyHex(walletName, password);
    return rpcPort.sendNativeTransfer(
        chainProfile, privateKeyHex, to, valueWei, gasLimit, gasPriceWei, data);
  }

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
