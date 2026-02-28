package com.evmcli.application.utils.rns;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

public final class lookup {
  private static final String RNS_REGISTRY_MAINNET = "0xcb868aeabd31e2b66f74e9a55cf064abb31a4ad5";
  private static final String RNS_REGISTRY_TESTNET = "0x7d284aaac6e925aad802a53c0c69efe3764597b8";
  private static final long CHAIN_ID_MAINNET = 30L;
  private static final long CHAIN_ID_TESTNET = 31L;
  private static final long COIN_TYPE_RSK = 60L;
  private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

  private lookup() {}

  public static Optional<String> lookup(String rpcUrl, String name) {
    try (Web3j web3j = Web3j.build(new HttpService(rpcUrl))) {
      String registry = resolveRegistryAddress(web3j);
      byte[] node = namehash(name);
      String resolver = registryResolver(web3j, registry, node);
      if (isZeroAddress(resolver)) {
        return Optional.empty();
      }
      String resolvedAddress = resolverAddr(web3j, resolver, node);
      if (isZeroAddress(resolvedAddress)) {
        return Optional.empty();
      }
      return Optional.of(normalizeAddress(resolvedAddress));
    } catch (Exception ex) {
      throw new IllegalStateException("RNS lookup failed for '" + name + "'", ex);
    }
  }

  public static Optional<String> reverseLookup(String rpcUrl, String address) {
    try (Web3j web3j = Web3j.build(new HttpService(rpcUrl))) {
      String registry = resolveRegistryAddress(web3j);
      String normalized = normalizeAddress(address);
      String reverseName = normalized.substring(2).toLowerCase(Locale.ROOT) + ".addr.reverse";
      byte[] reverseNode = namehash(reverseName);
      String resolver = registryResolver(web3j, registry, reverseNode);
      if (isZeroAddress(resolver)) {
        return Optional.empty();
      }
      String name = resolverName(web3j, resolver, reverseNode);
      if (name == null || name.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(name);
    } catch (Exception ex) {
      throw new IllegalStateException("RNS reverse lookup failed for '" + address + "'", ex);
    }
  }

  private static String resolveRegistryAddress(Web3j web3j) throws Exception {
    long chainId = web3j.ethChainId().send().getChainId().longValueExact();
    if (chainId == CHAIN_ID_MAINNET) {
      return RNS_REGISTRY_MAINNET;
    }
    if (chainId == CHAIN_ID_TESTNET) {
      return RNS_REGISTRY_TESTNET;
    }
    throw new IllegalStateException("Unsupported chain for RNS resolution: " + chainId);
  }

  private static String registryResolver(Web3j web3j, String registry, byte[] node) throws Exception {
    Function function =
        new Function(
            "resolver",
            List.of(new Bytes32(node)),
            List.of(TypeReference.create(Address.class)));
    return decodeAddress(call(web3j, registry, function));
  }

  private static String resolverAddr(Web3j web3j, String resolver, byte[] node) throws Exception {
    Function function =
        new Function(
            "addr",
            List.of(new Bytes32(node)),
            List.of(TypeReference.create(Address.class)));
    String resolved = decodeAddress(call(web3j, resolver, function));
    if (!isZeroAddress(resolved)) {
      return resolved;
    }
    return resolverAddrByCoinType(web3j, resolver, node);
  }

  private static String resolverAddrByCoinType(Web3j web3j, String resolver, byte[] node)
      throws Exception {
    Function function =
        new Function(
            "addr",
            List.of(new Bytes32(node), new Uint256(COIN_TYPE_RSK)),
            List.of(TypeReference.create(DynamicBytes.class)));
    List<Type> output = call(web3j, resolver, function);
    if (output.isEmpty()) {
      return ZERO_ADDRESS;
    }
    byte[] value = ((DynamicBytes) output.get(0)).getValue();
    if (value == null || value.length == 0) {
      return ZERO_ADDRESS;
    }
    if (value.length == 20) {
      return "0x" + Numeric.toHexStringNoPrefix(value);
    }
    if (value.length == 32) {
      byte[] last20 = Arrays.copyOfRange(value, 12, 32);
      return "0x" + Numeric.toHexStringNoPrefix(last20);
    }
    return ZERO_ADDRESS;
  }

  private static String resolverName(Web3j web3j, String resolver, byte[] node) throws Exception {
    Function function =
        new Function(
            "name",
            List.of(new Bytes32(node)),
            List.of(TypeReference.create(Utf8String.class)));
    List<Type> output = call(web3j, resolver, function);
    if (output.isEmpty()) {
      return null;
    }
    return ((Utf8String) output.get(0)).getValue();
  }

  private static List<Type> call(Web3j web3j, String to, Function function) throws Exception {
    String encoded = FunctionEncoder.encode(function);
    EthCall response =
        web3j.ethCall(
                Transaction.createEthCallTransaction(null, to, encoded),
                DefaultBlockParameterName.LATEST)
            .send();
    if (response.hasError()) {
      throw new IllegalStateException(response.getError().getMessage());
    }
    String value = response.getValue();
    if (value == null || value.isBlank() || "0x".equals(value)) {
      return Collections.emptyList();
    }
    return FunctionReturnDecoder.decode(value, function.getOutputParameters());
  }

  private static String decodeAddress(List<Type> output) {
    if (output.isEmpty()) {
      return ZERO_ADDRESS;
    }
    return normalizeAddress(((Address) output.get(0)).getValue());
  }

  private static String normalizeAddress(String value) {
    if (!Numeric.containsHexPrefix(value)) {
      return Numeric.prependHexPrefix(value);
    }
    return value;
  }

  private static boolean isZeroAddress(String value) {
    return normalizeAddress(value).equalsIgnoreCase(ZERO_ADDRESS);
  }

  private static byte[] namehash(String name) {
    byte[] node = new byte[32];
    if (name == null || name.isBlank()) {
      return node;
    }

    List<String> labels = new ArrayList<>(Arrays.asList(name.trim().split("\\.")));
    for (int i = labels.size() - 1; i >= 0; i--) {
      String label = labels.get(i);
      if (label.isBlank()) {
        continue;
      }
      byte[] labelHash = Hash.sha3(label.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
      byte[] toHash = new byte[64];
      System.arraycopy(node, 0, toHash, 0, 32);
      System.arraycopy(labelHash, 0, toHash, 32, 32);
      node = Hash.sha3(toHash);
    }
    return node;
  }
}
