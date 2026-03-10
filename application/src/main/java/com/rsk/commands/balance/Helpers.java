package com.rsk.commands.balance;

import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Contract;
import com.rsk.utils.Rpc;
import com.rsk.utils.Rns;
import com.rsk.utils.Storage.JsonConfigRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

public class Helpers {
  private static final BigDecimal WEI = new BigDecimal("1000000000000000000");

  private final com.rsk.commands.config.Helpers configHelpers;
  private final com.rsk.commands.wallet.Helpers walletHelpers;
  private final Rpc.RpcPort rpcPort;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers,
      Rpc.RpcPort rpcPort) {
    this.configHelpers = configHelpers;
    this.walletHelpers = walletHelpers;
    this.rpcPort = rpcPort;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(
        new com.rsk.commands.config.Helpers(new JsonConfigRepository(homeDir)),
        com.rsk.commands.wallet.Helpers.defaultHelpers(),
        new Rpc.Web3jRpcGateway());
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

    return Chain.resolve(
        configHelpers.loadConfig(), new Chain.ChainSelection(useMainnet, useTestnet, chainOption));
  }

  public String resolveWalletAddress(String walletName) {
    if (walletName != null && !walletName.isBlank()) {
      return walletHelpers.requireWallet(walletName).address();
    }
    throw new IllegalArgumentException("Wallet name is required.");
  }

  public String resolveActiveWalletAddress() {
    return walletHelpers
        .activeWallet()
        .map(WalletMetadata::address)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Provide --wallet, --address, or --rns, or set an active wallet."));
  }

  public String resolveAddressInput(ChainProfile chainProfile, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Address value is required.");
    }
    String trimmed = value.trim();
    if (Rns.isHexAddress(trimmed)) {
      return trimmed;
    }
    if (!Rns.isValidRnsName(trimmed)) {
      throw new IllegalArgumentException(
          "Invalid RNS format: " + trimmed + ". Expected domain-like format, e.g. alice.rsk");
    }
    return Rns.lookup(chainProfile.rpcUrl(), trimmed)
        .orElseThrow(() -> new IllegalArgumentException("RNS name not found: " + trimmed));
  }

  public BigInteger nativeBalanceWei(ChainProfile chainProfile, String address) {
    return rpcPort.getNativeBalance(chainProfile, address);
  }

  public BigDecimal toNative(BigInteger wei) {
    return new BigDecimal(wei).divide(WEI);
  }

  public String knownTokenAddress(String symbol, ChainProfile chainProfile) {
    return Contract.tokenAddress(symbol, chainProfile);
  }

  public TokenBalance tokenBalance(ChainProfile chainProfile, String holderAddress, String contractAddress) {
    String normalizedAddress = validateTokenAddress(contractAddress);

    try {
      com.rsk.commands.contract.Helpers contractHelpers =
          com.rsk.commands.contract.Helpers.defaultHelpers();
      List<Type> nameOut =
          contractHelpers.executeReadFunction(
              chainProfile,
              normalizedAddress,
              "name",
              List.of(),
              List.of(TypeReference.create(Utf8String.class)));
      List<Type> symbolOut =
          contractHelpers.executeReadFunction(
              chainProfile,
              normalizedAddress,
              "symbol",
              List.of(),
              List.of(TypeReference.create(Utf8String.class)));
      List<Type> decimalsOut =
          contractHelpers.executeReadFunction(
              chainProfile,
              normalizedAddress,
              "decimals",
              List.of(),
              List.of(TypeReference.create(Uint8.class)));
      List<Type> balanceOut =
          contractHelpers.executeReadFunction(
              chainProfile,
              normalizedAddress,
              "balanceOf",
              List.of(new Address(holderAddress)),
              List.of(TypeReference.create(Uint256.class)));

      if (symbolOut.isEmpty() || decimalsOut.isEmpty() || balanceOut.isEmpty()) {
        throw new IllegalArgumentException("Invalid contract address or contract not found");
      }

      String name = nameOut.isEmpty() ? "Unknown Token" : ((Utf8String) nameOut.get(0)).getValue();
      String symbol = ((Utf8String) symbolOut.get(0)).getValue();
      int decimals = ((Uint8) decimalsOut.get(0)).getValue().intValue();
      BigInteger balance = ((Uint256) balanceOut.get(0)).getValue();
      return new TokenBalance(name, symbol, normalizedAddress.toLowerCase(), balance, decimals);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid contract address or contract not found", ex);
    }
  }

  public String validateTokenAddress(String contractAddress) {
    if (contractAddress == null || contractAddress.isBlank()) {
      throw new IllegalArgumentException("Invalid contract address");
    }
    String normalized = contractAddress.trim();
    if (!Rns.isHexAddress(normalized)) {
      throw new IllegalArgumentException("Invalid contract address");
    }
    return normalized;
  }

  public BigDecimal tokenUnitsToDecimal(BigInteger value, int decimals) {
    return new BigDecimal(value).movePointLeft(decimals);
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

  public record TokenBalance(
      String name, String symbol, String contractAddress, BigInteger balance, int decimals) {}

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
