package com.rsk.commands.verify;

import com.rsk.commands.config.CliConfig;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainFeatures;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Rns;
import com.rsk.utils.Storage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Helpers {
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

  public void validateVerifyInput(
      String address,
      boolean autodetectConstructorArgs,
      String constructorArgs,
      List<String> decodedArgs) {
    if (!Rns.isHexAddress(address)) {
      throw new IllegalArgumentException("Invalid contract address: " + address);
    }
    if (!autodetectConstructorArgs) {
      boolean hasConstructorArgs = constructorArgs != null && !constructorArgs.isBlank();
      boolean hasDecodedArgs = decodedArgs != null && !decodedArgs.isEmpty();
      if (!hasConstructorArgs && hasDecodedArgs) {
        throw new IllegalArgumentException(
            "--decodedArgs is not supported by Blockscout standard-input verify. Provide --constructor-args (hex).");
      }
      if (!hasConstructorArgs) {
        throw new IllegalArgumentException(
            "When --autodetect-constructor-args=false, --constructor-args is required.");
      }
    }
  }

  public byte[] readJsonFile(String path) {
    try {
      return Files.readAllBytes(Path.of(path));
    } catch (IOException ex) {
      throw new IllegalArgumentException("Unable to read JSON file: " + path, ex);
    }
  }

  public String blockscoutVerifyUrl(ChainProfile chainProfile, String address) {
    if (chainProfile.chainId() == 30L) {
      return "https://rootstock.blockscout.com/api/v2/smart-contracts/"
          + address
          + "/verification/via/standard-input";
    }
    if (chainProfile.chainId() == 31L) {
      return "https://rootstock-testnet.blockscout.com/api/v2/smart-contracts/"
          + address
          + "/verification/via/standard-input";
    }
    throw new IllegalArgumentException("Contract verification is only supported on Rootstock mainnet/testnet.");
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

  public void submitVerification(
      ChainProfile chainProfile,
      String jsonPath,
      String contractName,
      String address,
      String compilerVersion,
      String licenseType,
      boolean autodetectConstructorArgs,
      String constructorArgs) {
    String endpoint = blockscoutVerifyUrl(chainProfile, address);
    byte[] jsonFile = readJsonFile(jsonPath);

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("compiler_version", compilerVersion);
    fields.put("contract_name", contractName);
    fields.put("license_type", licenseType);
    fields.put("autodetect_constructor_args", Boolean.toString(autodetectConstructorArgs));
    if (!autodetectConstructorArgs && constructorArgs != null && !constructorArgs.isBlank()) {
      fields.put("constructor_args", constructorArgs);
    }

    String boundary = "----rskjavacli-" + UUID.randomUUID();
    HttpRequest.BodyPublisher body =
        multipartBody(boundary, fields, "files[0]", Path.of(jsonPath).getFileName().toString(), jsonFile);

    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint))
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(body)
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Verification API error " + response.statusCode() + ": " + response.body());
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to verify contract.", ex);
    }
  }

  private static HttpRequest.BodyPublisher multipartBody(
      String boundary, Map<String, String> fields, String fileField, String filename, byte[] fileBytes) {
    List<byte[]> byteArrays = new java.util.ArrayList<>();
    String separator = "--" + boundary + "\r\n";

    fields.forEach(
        (name, value) -> {
          String part =
              separator
                  + "Content-Disposition: form-data; name=\""
                  + name
                  + "\"\r\n\r\n"
                  + value
                  + "\r\n";
          byteArrays.add(part.getBytes(StandardCharsets.UTF_8));
        });

    String fileHeader =
        separator
            + "Content-Disposition: form-data; name=\""
            + fileField
            + "\"; filename=\""
            + filename
            + "\"\r\n"
            + "Content-Type: application/json\r\n\r\n";
    byteArrays.add(fileHeader.getBytes(StandardCharsets.UTF_8));
    byteArrays.add(fileBytes);
    byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));

    String ending = "--" + boundary + "--\r\n";
    byteArrays.add(ending.getBytes(StandardCharsets.UTF_8));
    return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
  }

}
