package com.rsk.utils;

import com.rsk.commands.contract.Helpers;
import com.rsk.utils.Chain.ChainProfile;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Contract {
  private static final Logger LOGGER = LoggerFactory.getLogger(Contract.class);
  public static final Map<String, Map<String, String>> TOKENS = createTokens();

  private Contract() {}

  public static String tokenAddress(String symbol, ChainProfile chainProfile) {
    Map<String, String> networks = TOKENS.get(symbol);
    if (networks == null) {
      throw new IllegalArgumentException("Unknown token: " + symbol);
    }

    String address = networks.get(networkKey(chainProfile));
    if (address == null || address.isBlank()) {
      throw new IllegalArgumentException(
          "No configured "
              + symbol
              + " address for network "
              + chainProfile.name()
              + ".");
    }
    return address;
  }

  public static boolean hasToken(String symbol) {
    return TOKENS.containsKey(symbol);
  }

  public static String resolveTokenAddress(String tokenOption, ChainProfile chainProfile) {
    if (tokenOption == null || tokenOption.isBlank()) {
      return null;
    }
    String trimmed = tokenOption.trim();
    if ("rbtc".equalsIgnoreCase(trimmed)) {
      return null;
    }
    if (hasToken(trimmed)) {
      return tokenAddress(trimmed, chainProfile);
    }
    if (!trimmed.matches("^0x[a-fA-F0-9]{40}$")) {
      throw new IllegalArgumentException("Invalid token contract address");
    }
    return trimmed;
  }

  public static TokenMetadata readTokenMetadata(ChainProfile chainProfile, String tokenAddress) {
    try {
      Helpers helpers = Helpers.defaultHelpers();
      List<Type> symbolOut =
          helpers.executeReadFunction(
              chainProfile,
              tokenAddress,
              "symbol",
              List.of(),
              List.of(TypeReference.create(Utf8String.class)));
      List<Type> decimalsOut =
          helpers.executeReadFunction(
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
      LOGGER.warn(
          "Unable to read token metadata for {} on chain {}",
          tokenAddress,
          chainProfile.chainId(),
          ex);
      throw new IllegalArgumentException("Invalid token contract address", ex);
    }
  }

  public static BigInteger tokenAmountToUnits(BigDecimal value, int decimals) {
    return Format.decimalToUnits(value, decimals);
  }

  public static String encodeErc20Transfer(String to, BigInteger amountUnits) {
    Function function =
        new Function("transfer", List.of(new Address(to), new Uint256(amountUnits)), List.of());
    return FunctionEncoder.encode(function);
  }

  public static BigInteger estimateTokenTransferGas(
      ChainProfile chainProfile,
      String fromAddress,
      String tokenAddress,
      String encodedData,
      BigInteger gasPriceWei) {
    try {
      Web3j web3j = Rpc.web3j(chainProfile);
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
        LOGGER.debug(
            "Falling back to default gas estimate for token transfer to {} on chain {}",
            tokenAddress,
            chainProfile.chainId());
        return BigInteger.valueOf(100_000L);
      }
      return estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    } catch (Exception ex) {
      LOGGER.warn(
          "Unable to estimate token transfer gas for {} on chain {}, using fallback",
          tokenAddress,
          chainProfile.chainId(),
          ex);
      return BigInteger.valueOf(100_000L);
    }
  }

  public static String networkKey(ChainProfile chainProfile) {
    if (chainProfile.chainId() == 30L) {
      return "mainnet";
    }
    if (chainProfile.chainId() == 31L) {
      return "testnet";
    }
    throw new IllegalArgumentException(
        "Known token symbols are only supported on Rootstock mainnet (30) and testnet (31).");
  }

  public record TokenMetadata(String symbol, int decimals) {}

  private static Map<String, Map<String, String>> createTokens() {
    Map<String, Map<String, String>> tokens = new LinkedHashMap<>();
    tokens.put(
        "RIF",
        Map.of(
            "mainnet", "0x2acc95758f8b5F583470ba265eb685a8f45fc9d5",
            "testnet", "0x19f64674d8a5b4e652319f5e239efd3bc969a1fe"));
    tokens.put(
        "USDRIF",
        Map.of(
            "mainnet", "0x3A15461d8ae0f0fb5fa2629e9da7D66a794a6e37",
            "testnet", "0xd1b0d1bc03491f49b9aea967ddd07b37f7327e63"));
    tokens.put(
        "DoC",
        Map.of(
            "mainnet", "0xe700691da7B9851f2f35f8b8182c69c53ccad9db",
            "testnet", "0xd37a3e5874be2dc6c732ad21c008a1e4032a6040"));
    return tokens;
  }
}
