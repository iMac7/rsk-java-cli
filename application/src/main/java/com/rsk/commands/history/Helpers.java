package com.rsk.commands.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Constants;
import com.rsk.utils.Json;
import com.rsk.utils.Storage;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

public class Helpers {
  private static final ObjectMapper OBJECT_MAPPER = Json.ObjectMapperFactory.create();
  static final String ALCHEMY_API_KEY_ENV = "RSK_CLI_ALCHEMY_API_KEY";

  private final com.rsk.commands.config.Helpers configHelpers;

  public Helpers(com.rsk.commands.config.Helpers configHelpers) {
    this.configHelpers = configHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)));
  }

  public ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    return Chain.resolveChain(configHelpers.loadConfig(), mainnet, testnet, chain, chainUrl);
  }

  public String resolveApiKey(String explicitApiKey) {
    if (explicitApiKey != null && !explicitApiKey.isBlank()) {
      return explicitApiKey;
    }
    String envApiKey = System.getenv(ALCHEMY_API_KEY_ENV);
    if (envApiKey != null && !envApiKey.isBlank()) {
      return envApiKey;
    }
    String key = configHelpers.loadConfig().getApiKeys().getAlchemyApiKey();
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
          "Alchemy API key is required. Provide --apikey, set "
              + ALCHEMY_API_KEY_ENV
              + ", or set config.apiKeys.alchemyApiKey.");
    }
    return key;
  }

  public String normalizeHexCount(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      return "0x" + trimmed.substring(2).toLowerCase();
    }
    try {
      return "0x" + new BigInteger(trimmed).toString(16);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid maxCount/number value: " + value);
    }
  }

  public JsonNode alchemyAssetTransfersRequest(
      String fromBlock,
      String toBlock,
      String fromAddress,
      String toAddress,
      String excludeZeroValue,
      String categoryCsv,
      String maxCountHex,
      String order,
      String contractAddressesCsv,
      String pageKey) {
    var root = OBJECT_MAPPER.createObjectNode();
    root.put("jsonrpc", "2.0");
    root.put("id", 1);
    root.put("method", "alchemy_getAssetTransfers");
    var paramsArray = root.putArray("params");
    var params = paramsArray.addObject();

    if (fromBlock != null && !fromBlock.isBlank()) {
      params.put("fromBlock", fromBlock);
    }
    if (toBlock != null && !toBlock.isBlank()) {
      params.put("toBlock", toBlock);
    }
    if (fromAddress != null && !fromAddress.isBlank()) {
      params.put("fromAddress", fromAddress);
    }
    if (toAddress != null && !toAddress.isBlank()) {
      params.put("toAddress", toAddress);
    }
    if (excludeZeroValue != null && !excludeZeroValue.isBlank()) {
      params.put("excludeZeroValue", Boolean.parseBoolean(excludeZeroValue));
    }
    if (maxCountHex != null && !maxCountHex.isBlank()) {
      params.put("maxCount", maxCountHex);
    }
    if (order != null && !order.isBlank()) {
      params.put("order", order);
    }
    if (pageKey != null && !pageKey.isBlank()) {
      params.put("pageKey", pageKey);
    }

    List<String> categories = splitCsv(categoryCsv);
    if (!categories.isEmpty()) {
      var arr = params.putArray("category");
      categories.forEach(arr::add);
    }

    List<String> contracts = splitCsv(contractAddressesCsv);
    if (!contracts.isEmpty()) {
      var arr = params.putArray("contractAddresses");
      contracts.forEach(arr::add);
    }
    return root;
  }

  public String resolveAlchemyUrl(ChainProfile chainProfile, String apiKey) {
    if (chainProfile.chainId() == 30L) {
      return Constants.ALCHEMY_ROOTSTOCK_MAINNET_URL + "/" + apiKey;
    }
    if (chainProfile.chainId() == 31L) {
      return Constants.ALCHEMY_ROOTSTOCK_TESTNET_URL + "/" + apiKey;
    }
    throw new IllegalArgumentException(
        "Alchemy asset history is only supported for Rootstock mainnet/testnet.");
  }

  public JsonNode postJson(String url, JsonNode body) {
    try {
      HttpClient client = HttpClient.newHttpClient();
      String requestBody = OBJECT_MAPPER.writeValueAsString(body);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("Alchemy HTTP error " + response.statusCode() + ": " + response.body());
      }
      return OBJECT_MAPPER.readTree(response.body());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to call Alchemy asset history API.", ex);
    }
  }

  public String prettyPrint(JsonNode response) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to print Alchemy response.", ex);
    }
  }

  private static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
  }

}
