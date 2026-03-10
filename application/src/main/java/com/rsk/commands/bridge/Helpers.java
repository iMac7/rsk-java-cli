package com.rsk.commands.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.commands.config.CliConfig;
import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Json;
import com.rsk.utils.Storage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

public class Helpers {
  private static final String RSK_BRIDGE_CONTRACT = "0x0000000000000000000000000000000001000006";
  private static final String BRIDGE_ABI_RESOURCE = "bridge_abi.json";
  private static final ObjectMapper OBJECT_MAPPER = Json.ObjectMapperFactory.create();

  private final com.rsk.commands.config.Helpers configHelpers;
  private final com.rsk.commands.wallet.Helpers walletHelpers;
  private final com.rsk.commands.contract.Helpers contractHelpers;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers,
      com.rsk.commands.contract.Helpers contractHelpers) {
    this.configHelpers = configHelpers;
    this.walletHelpers = walletHelpers;
    this.contractHelpers = contractHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(
        new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)),
        com.rsk.commands.wallet.Helpers.defaultHelpers(),
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
    return Chain.resolve(
        config, new Chain.ChainSelection(useMainnet, useTestnet, chainOption));
  }

  public String bridgeAddress() {
    return RSK_BRIDGE_CONTRACT;
  }

  public JsonNode readAbiArrayResource() {
    try (var in = Helpers.class.getClassLoader().getResourceAsStream(BRIDGE_ABI_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("ABI resource not found: " + BRIDGE_ABI_RESOURCE);
      }
      JsonNode root = OBJECT_MAPPER.readTree(in);
      if (!root.isArray()) {
        throw new IllegalStateException("ABI resource must be a JSON array: " + BRIDGE_ABI_RESOURCE);
      }
      return root;
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read ABI resource: " + BRIDGE_ABI_RESOURCE, ex);
    }
  }

  public List<JsonNode> functions(JsonNode abiArray) {
    List<JsonNode> functions = new ArrayList<>();
    for (JsonNode entry : abiArray) {
      if ("function".equals(entry.path("type").asText())) {
        functions.add(entry);
      }
    }
    return functions;
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

  public String dumpPrivateKey(String walletName, char[] password) {
    return walletHelpers.dumpPrivateKey(walletName, password);
  }

  public List<Type> parseFunctionInputs(
      JsonNode functionNode, java.util.function.BiFunction<String, String, String> prompt) {
    List<Type> inputs = new ArrayList<>();
    JsonNode inputNodes = functionNode.path("inputs");
    if (!inputNodes.isArray()) {
      return inputs;
    }
    for (int i = 0; i < inputNodes.size(); i++) {
      JsonNode input = inputNodes.get(i);
      String type = input.path("type").asText();
      String argName = input.path("name").asText();
      String label = (argName == null || argName.isBlank()) ? ("arg" + i) : argName;
      String raw = prompt.apply(label, type);
      inputs.add(contractHelpers.toAbiInputType(type, raw));
    }
    return inputs;
  }

  public List<TypeReference<?>> parseFunctionOutputs(JsonNode functionNode) {
    List<TypeReference<?>> outputRefs = new ArrayList<>();
    JsonNode outputNodes = functionNode.path("outputs");
    if (!outputNodes.isArray()) {
      return outputRefs;
    }
    for (JsonNode output : outputNodes) {
      outputRefs.add(contractHelpers.outputTypeReference(output.path("type").asText()));
    }
    return outputRefs;
  }

  public List<Type> executeRead(
      ChainProfile chainProfile,
      String contractAddress,
      JsonNode functionNode,
      java.util.function.BiFunction<String, String, String> prompt) {
    String functionName = functionNode.path("name").asText();
    List<Type> inputs = parseFunctionInputs(functionNode, prompt);
    List<TypeReference<?>> outputRefs = parseFunctionOutputs(functionNode);
    return contractHelpers.executeReadFunction(chainProfile, contractAddress, functionName, inputs, outputRefs);
  }

  public WriteResult executeWrite(
      ChainProfile chainProfile,
      String contractAddress,
      JsonNode functionNode,
      String walletName,
      char[] password,
      BigDecimal value,
      BigInteger gasLimit,
      BigInteger gasPrice,
      java.util.function.BiFunction<String, String, String> prompt)
      throws Exception {
    String privateKeyHex = dumpPrivateKey(walletName, password);
    Credentials credentials = Credentials.create(privateKeyHex);
    List<Type> inputs = parseFunctionInputs(functionNode, prompt);
    Function function = new Function(functionNode.path("name").asText(), inputs, List.of());
    String dataHex = FunctionEncoder.encode(function);

    try (Web3j web3j = Web3j.build(new HttpService(chainProfile.rpcUrl()))) {
      BigInteger txValue = value == null ? BigInteger.ZERO : decimalToUnits(value, 18);
      EthGetTransactionCount nonceResponse =
          web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING).send();
      BigInteger nonce = nonceResponse.getTransactionCount();
      BigInteger txGasPrice = gasPrice != null ? gasPrice : web3j.ethGasPrice().send().getGasPrice();
      BigInteger txGasLimit;
      if (gasLimit != null) {
        txGasLimit = gasLimit;
      } else {
        EthEstimateGas estimate =
            web3j.ethEstimateGas(
                    Transaction.createFunctionCallTransaction(
                        credentials.getAddress(), nonce, txGasPrice, null, contractAddress, txValue, dataHex))
                .send();
        if (estimate.hasError() || estimate.getAmountUsed() == null) {
          txGasLimit = BigInteger.valueOf(300_000L);
        } else {
          txGasLimit = estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
        }
      }
      RawTransaction tx =
          RawTransaction.createTransaction(nonce, txGasPrice, txGasLimit, contractAddress, txValue, dataHex);
      byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
      EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
      if (sent.hasError()) {
        throw new IllegalStateException(sent.getError().getMessage());
      }
      String txHash = sent.getTransactionHash();
      TransactionReceipt receipt = waitForReceipt(web3j, txHash, 120, 2000L);
      if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
        throw new IllegalStateException("Transaction failed. Receipt status: " + receipt.getStatus());
      }
      return new WriteResult(
          credentials.getAddress(),
          txHash,
          receipt.getBlockNumber().toString(),
          receipt.getGasUsed().toString());
    }
  }

  public String readableTypeValue(Type type) {
    return contractHelpers.readableTypeValue(type);
  }

  public String explorerTxUrl(ChainProfile chainProfile, String txHash) {
    String template = chainProfile.explorerTxUrlTemplate();
    if (template == null || template.isBlank()) {
      return "(explorer URL not configured)";
    }
    if (template.contains("%s")) {
      return String.format(template, txHash);
    }
    return template.endsWith("/") ? template + txHash : template + "/" + txHash;
  }

  public String explorerAddressUrl(ChainProfile chainProfile, String address) {
    return contractHelpers.blockscoutAddressUrl(chainProfile, address);
  }

  public record WriteResult(String walletAddress, String txHash, String blockNumber, String gasUsed) {}

  private static BigInteger decimalToUnits(BigDecimal value, int decimals) {
    try {
      return value.movePointRight(decimals).toBigIntegerExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException(
          "Too many decimal places for token decimals=" + decimals + ": " + value, ex);
    }
  }

  private static TransactionReceipt waitForReceipt(
      Web3j web3j, String txHash, int maxPolls, long sleepMs) throws Exception {
    for (int i = 0; i < maxPolls; i++) {
      var receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
      if (receiptResponse.getTransactionReceipt().isPresent()) {
        return receiptResponse.getTransactionReceipt().get();
      }
      Thread.sleep(sleepMs);
    }
    throw new IllegalStateException("Timed out waiting for transaction receipt: " + txHash);
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
