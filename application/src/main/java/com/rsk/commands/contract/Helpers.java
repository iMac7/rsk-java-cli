package com.rsk.commands.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.commands.config.CliConfig;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Json;
import com.rsk.utils.Rns;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

public class Helpers {
  private static final ObjectMapper OBJECT_MAPPER = Json.ObjectMapperFactory.create();

  private final com.rsk.commands.config.Helpers configHelpers;

  public Helpers(com.rsk.commands.config.Helpers configHelpers) {
    this.configHelpers = configHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(
        new com.rsk.commands.config.Helpers(new com.rsk.utils.Storage.JsonConfigRepository(homeDir)));
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
    return Chain.resolve(
        config, new Chain.ChainSelection(useMainnet, useTestnet || !useMainnet, chainOption));
  }

  public void validateAddress(String address) {
    if (!Rns.isHexAddress(address)) {
      throw new IllegalArgumentException("Invalid contract address: " + address);
    }
  }

  public JsonNode resolveAbiArrayFromBlockscout(ChainProfile chainProfile, String address) {
    List<String> urls = List.of(blockscoutContractUrl(chainProfile, address), blockscoutAddressApiUrl(chainProfile, address));
    Exception lastError = null;
    for (String url : urls) {
      try {
        JsonNode payload = getJson(url);
        JsonNode abiNode = findAbiNodeInPayload(payload);
        if (abiNode == null) {
          continue;
        }
        if (abiNode.isArray()) {
          return abiNode;
        }
        if (abiNode.isTextual()) {
          JsonNode parsed = OBJECT_MAPPER.readTree(abiNode.asText());
          if (parsed.isArray()) {
            return parsed;
          }
        }
      } catch (Exception ex) {
        lastError = ex;
      }
    }
    throw new IllegalStateException("Unable to resolve contract ABI from Blockscout.", lastError);
  }

  public List<JsonNode> readFunctions(JsonNode abiArray) {
    List<JsonNode> readFunctions = new ArrayList<>();
    for (JsonNode entry : abiArray) {
      if (!"function".equals(entry.path("type").asText())) {
        continue;
      }
      String mutability = entry.path("stateMutability").asText();
      if ("view".equals(mutability) || "pure".equals(mutability)) {
        readFunctions.add(entry);
      }
    }
    return readFunctions;
  }

  public List<Type> executeReadFunction(
      ChainProfile chainProfile, String contractAddress, String functionName, List<Type> inputs, List<TypeReference<?>> outputRefs) {
    Function function = new Function(functionName, inputs, outputRefs);
    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
      return ethCall(web3j, null, contractAddress, function);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to call read function.", ex);
    }
  }

  public Type<?> toAbiInputType(String solidityType, String value) {
    if ("bytes[]".equals(solidityType)) {
      List<DynamicBytes> items =
          splitCsv(value).stream().map(Numeric::hexStringToByteArray).map(DynamicBytes::new).toList();
      return new DynamicArray<>(DynamicBytes.class, items);
    }
    if ("bytes32[]".equals(solidityType)) {
      List<Bytes32> items =
          splitCsv(value).stream()
              .map(v -> new Bytes32(Numeric.toBytesPadded(Numeric.toBigInt(v), 32)))
              .toList();
      return new DynamicArray<>(Bytes32.class, items);
    }
    return switch (solidityType) {
      case "address" -> new Address(value);
      case "bool" -> new Bool(Boolean.parseBoolean(value));
      case "string" -> new Utf8String(value);
      case "bytes" -> new DynamicBytes(Numeric.hexStringToByteArray(value));
      case "bytes32" -> new Bytes32(Numeric.toBytesPadded(Numeric.toBigInt(value), 32));
      case "uint256", "uint" -> new Uint256(new BigInteger(value));
      case "uint8" -> new Uint8(new BigInteger(value));
      case "int256", "int", "int64" -> new Int256(new BigInteger(value));
      default ->
          throw new IllegalArgumentException(
              "Unsupported input type: "
                  + solidityType
                  + ". Supported: address,bool,string,bytes,bytes32,bytes[],bytes32[],uint8,uint,uint256,int,int64,int256");
    };
  }

  public TypeReference<?> outputTypeReference(String solidityType) {
    return switch (solidityType) {
      case "address" -> TypeReference.create(Address.class);
      case "bool" -> TypeReference.create(Bool.class);
      case "string" -> TypeReference.create(Utf8String.class);
      case "bytes" -> TypeReference.create(DynamicBytes.class);
      case "bytes32" -> TypeReference.create(Bytes32.class);
      case "uint256", "uint" -> TypeReference.create(Uint256.class);
      case "uint8" -> TypeReference.create(Uint8.class);
      case "int256", "int", "int64" -> TypeReference.create(Int256.class);
      default ->
          throw new IllegalArgumentException(
              "Unsupported output type: "
                  + solidityType
                  + ". Supported: address,bool,string,bytes,bytes32,uint8,uint,uint256,int,int64,int256");
    };
  }

  public String readableTypeValue(Type type) {
    if (type instanceof Utf8String s) {
      return s.getValue();
    }
    if (type instanceof Address a) {
      return a.getValue();
    }
    if (type instanceof Bool b) {
      return Boolean.toString(b.getValue());
    }
    return type.getValue() == null ? "null" : type.getValue().toString();
  }

  public String blockscoutAddressUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return "https://rootstock.blockscout.com/address/" + address;
    }
    if (chainProfile.chainId() == 31L) {
      return "https://rootstock-testnet.blockscout.com/address/" + address;
    }
    String template = chainProfile.explorerAddressUrlTemplate();
    if (template == null || template.isBlank()) {
      return "(explorer URL not configured)";
    }
    if (template.contains("%s")) {
      return String.format(template, address);
    }
    return template.endsWith("/") ? template + address : template + "/" + address;
  }

  private static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  private static String blockscoutContractUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return "https://rootstock.blockscout.com/api/v2/smart-contracts/" + address;
    }
    if (chainProfile.chainId() == 31L) {
      return "https://rootstock-testnet.blockscout.com/api/v2/smart-contracts/" + address;
    }
    throw new IllegalArgumentException("Contract interaction is only supported on Rootstock mainnet/testnet.");
  }

  private static String blockscoutAddressApiUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return "https://rootstock.blockscout.com/api/v2/addresses/" + address;
    }
    if (chainProfile.chainId() == 31L) {
      return "https://rootstock-testnet.blockscout.com/api/v2/addresses/" + address;
    }
    throw new IllegalArgumentException("Blockscout API is only supported on Rootstock mainnet/testnet.");
  }

  private static JsonNode getJson(String url) {
    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
      }
      return OBJECT_MAPPER.readTree(response.body());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to fetch contract metadata from Blockscout.", ex);
    }
  }

  private static JsonNode findAbiNodeInPayload(JsonNode payload) {
    if (payload == null || payload.isMissingNode() || payload.isNull()) {
      return null;
    }
    JsonNode directAbi = payload.path("abi");
    if (!directAbi.isMissingNode() && !directAbi.isNull()) {
      return directAbi;
    }
    JsonNode smartContractAbi = payload.path("smart_contract").path("abi");
    if (!smartContractAbi.isMissingNode() && !smartContractAbi.isNull()) {
      return smartContractAbi;
    }
    JsonNode contractAbi = payload.path("contract").path("abi");
    if (!contractAbi.isMissingNode() && !contractAbi.isNull()) {
      return contractAbi;
    }
    JsonNode resultAbi = payload.path("result").path("abi");
    if (!resultAbi.isMissingNode() && !resultAbi.isNull()) {
      return resultAbi;
    }
    return null;
  }

  private static List<Type> ethCall(Web3j web3j, String from, String to, Function function)
      throws Exception {
    String encoded = FunctionEncoder.encode(function);
    var response =
        web3j
            .ethCall(
                Transaction.createEthCallTransaction(from, to, encoded),
                DefaultBlockParameterName.LATEST)
            .send();
    if (response.hasError()) {
      throw new IllegalStateException(response.getError().getMessage());
    }
    return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
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
