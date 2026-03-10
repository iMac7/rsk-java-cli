package com.rsk.utils;

import com.rsk.utils.Chain.ChainProfile;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Contract {
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

  public static String networkKey(ChainProfile chainProfile) {
    return chainProfile.chainId() == 31L ? "testnet" : "mainnet";
  }

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
