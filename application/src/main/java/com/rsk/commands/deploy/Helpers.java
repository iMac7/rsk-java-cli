package com.rsk.commands.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.commands.wallet.Helpers.WalletMetadata;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Json;
import com.rsk.utils.Rpc;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
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
import org.web3j.utils.Numeric;

public class Helpers {
  private static final ObjectMapper OBJECT_MAPPER = Json.ObjectMapperFactory.create();

  private final com.rsk.commands.config.Helpers configHelpers;
  private final com.rsk.commands.wallet.Helpers walletHelpers;

  public Helpers(
      com.rsk.commands.config.Helpers configHelpers,
      com.rsk.commands.wallet.Helpers walletHelpers) {
    this.configHelpers = configHelpers;
    this.walletHelpers = walletHelpers;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(
        new com.rsk.commands.config.Helpers(new com.rsk.utils.Storage.JsonConfigRepository(homeDir)),
        com.rsk.commands.wallet.Helpers.defaultHelpers());
  }

  public ChainProfile resolveChain(
      boolean mainnet, boolean testnet, String chain, String chainUrl) {
    return Chain.resolveChain(configHelpers.loadConfig(), mainnet, testnet, chain, chainUrl);
  }

  public String activeWalletName() {
    return walletHelpers
        .activeWallet()
        .map(WalletMetadata::name)
        .orElseThrow(() -> new IllegalArgumentException("No active wallet found. Provide --wallet."));
  }

  public String dumpPrivateKey(String walletName, char[] password) {
    return walletHelpers.dumpPrivateKey(walletName, password);
  }

  public String readRequiredFile(String path, String label) {
    try {
      String content = Files.readString(Path.of(path));
      if (content == null || content.isBlank()) {
        throw new IllegalArgumentException(label + " file is empty: " + path);
      }
      return content;
    } catch (IOException ex) {
      throw new IllegalArgumentException("Unable to read " + label + " file: " + path, ex);
    }
  }

  public String buildDeploymentData(String bytecodeRaw, String abiJson, List<String> args) {
    String bytecode = bytecodeRaw.trim();
    if (!Numeric.containsHexPrefix(bytecode)) {
      bytecode = Numeric.prependHexPrefix(bytecode);
    }
    List<ConstructorInput> constructorInputs = constructorInputsFromAbi(abiJson);
    List<String> providedArgs = args == null ? List.of() : args;
    if (constructorInputs.size() != providedArgs.size()) {
      throw new IllegalArgumentException(
          "Constructor argument count mismatch. Expected "
              + constructorInputs.size()
              + " but got "
              + providedArgs.size()
              + ".");
    }
    if (constructorInputs.isEmpty()) {
      return bytecode;
    }

    List<Type> constructorArgs = new ArrayList<>();
    for (int i = 0; i < constructorInputs.size(); i++) {
      constructorArgs.add(toAbiType(constructorInputs.get(i).type(), providedArgs.get(i)));
    }
    String encoded = FunctionEncoder.encodeConstructor(constructorArgs);
    return bytecode + Numeric.cleanHexPrefix(encoded);
  }

  public List<ConstructorInput> constructorInputs(String abiJson) {
    return constructorInputsFromAbi(abiJson);
  }

  public DeploymentResult deployContract(
      ChainProfile chainProfile, String privateKeyHex, String deploymentData) {
    Chain.validateChainId(chainProfile, "Contract deployment");
    Credentials credentials = Credentials.create(privateKeyHex);
    try {
      Web3j web3j = Rpc.web3j(chainProfile);
      EthGetTransactionCount nonceResponse =
          web3j
              .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
              .send();
      BigInteger nonce = nonceResponse.getTransactionCount();
      BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
      BigInteger gasLimit = estimateDeployGas(web3j, credentials.getAddress(), deploymentData);

      RawTransaction tx =
          RawTransaction.createContractTransaction(
              nonce, gasPrice, gasLimit, BigInteger.ZERO, deploymentData);
      byte[] signed = TransactionEncoder.signMessage(tx, chainProfile.chainId(), credentials);
      String payload = Numeric.toHexString(signed);

      EthSendTransaction sent = web3j.ethSendRawTransaction(payload).send();
      if (sent.hasError()) {
        throw new IllegalStateException(sent.getError().getMessage());
      }

      String txHash = sent.getTransactionHash();
      TransactionReceipt receipt =
          com.rsk.utils.Transaction.waitForSuccessfulReceipt(web3j, txHash, 120, 2000L);
      String contractAddress = receipt.getContractAddress();
      if (contractAddress == null || contractAddress.isBlank()) {
        throw new IllegalStateException("Deployment receipt did not include a contract address.");
      }
      return new DeploymentResult(txHash, contractAddress, explorerAddressUrl(chainProfile, contractAddress));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to deploy contract.", ex);
    }
  }

  public record DeploymentResult(String txHash, String contractAddress, String explorerUrl) {}

  public record ConstructorInput(String name, String type) {}

  private static List<ConstructorInput> constructorInputsFromAbi(String abiJson) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(abiJson);
      if (!root.isArray()) {
        throw new IllegalArgumentException("ABI must be a JSON array.");
      }
      for (JsonNode item : root) {
        if ("constructor".equals(item.path("type").asText())) {
          JsonNode inputs = item.path("inputs");
          if (!inputs.isArray()) {
            return List.of();
          }
          List<ConstructorInput> constructorInputs = new ArrayList<>();
          for (JsonNode input : inputs) {
            String type = input.path("type").asText();
            if (type == null || type.isBlank()) {
              throw new IllegalArgumentException("Constructor input is missing type.");
            }
            String name = input.path("name").asText();
            constructorInputs.add(
                new ConstructorInput(name == null || name.isBlank() ? "" : name, type));
          }
          return constructorInputs;
        }
      }
      return List.of();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid ABI JSON.", ex);
    }
  }

  private static Type<?> toAbiType(String solidityType, String value) {
    return switch (solidityType) {
      case "address" -> new Address(value);
      case "bool" -> new Bool(Boolean.parseBoolean(value));
      case "string" -> new Utf8String(value);
      case "bytes" -> new DynamicBytes(Numeric.hexStringToByteArray(value));
      case "bytes32" -> new Bytes32(Numeric.toBytesPadded(Numeric.toBigInt(value), 32));
      case "uint256" -> new Uint256(new BigInteger(value));
      case "int256" -> new Int256(new BigInteger(value));
      default ->
          throw new IllegalArgumentException(
              "Unsupported constructor type: "
                  + solidityType
                  + ". Supported: address,bool,string,bytes,bytes32,uint256,int256");
    };
  }

  private static BigInteger estimateDeployGas(Web3j web3j, String from, String data) {
    try {
      EthEstimateGas estimate =
          web3j
              .ethEstimateGas(
                  Transaction.createContractTransaction(from, null, null, null, BigInteger.ZERO, data))
              .send();
      if (estimate.hasError() || estimate.getAmountUsed() == null) {
        return BigInteger.valueOf(3_000_000L);
      }
      return estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    } catch (Exception ex) {
      return BigInteger.valueOf(3_000_000L);
    }
  }

  private static String explorerAddressUrl(ChainProfile chainProfile, String address) {
    return Chain.explorerUrl(chainProfile, address, false);
  }

}
