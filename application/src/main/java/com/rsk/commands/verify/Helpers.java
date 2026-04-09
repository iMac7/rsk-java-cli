package com.rsk.commands.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.commands.config.Helpers.ChainResolutionSupport;
import com.rsk.utils.Chain;
import com.rsk.utils.Chain.ChainProfile;
import com.rsk.utils.Json;
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

public class Helpers extends ChainResolutionSupport {
  private static final ObjectMapper OBJECT_MAPPER = Json.ObjectMapperFactory.create();

  public Helpers(com.rsk.commands.config.Helpers configHelpers) {
    super(configHelpers);
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".rsk-java-cli");
    return new Helpers(new com.rsk.commands.config.Helpers(new Storage.JsonConfigRepository(homeDir)));
  }

  public void validateVerifyInput(String address) {
    if (!Rns.isHexAddress(address)) {
      throw new IllegalArgumentException("Invalid contract address: " + address);
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
    return Chain.blockscoutUrl(chainProfile)
        + "/api/v2/smart-contracts/"
        + address
        + "/verification/via/standard-input";
  }

  public String blockscoutAddressUrl(ChainProfile chainProfile, String address) {
    return Chain.blockscoutAddressUrl(chainProfile, address);
  }

  public void submitVerification(
      ChainProfile chainProfile, String jsonPath, String contractName, String address) {
    String endpoint = blockscoutVerifyUrl(chainProfile, address);
    byte[] jsonFile = readJsonFile(jsonPath);
    String compilerVersion = extractCompilerVersion(jsonFile);

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("compiler_version", compilerVersion);
    fields.put("contract_name", contractName);
    fields.put("autodetect_constructor_args", "true");

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

  private String extractCompilerVersion(byte[] jsonFile) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(jsonFile);
      List<String> candidates =
          List.of(
              root.path("compiler_version").asText(),
              root.path("compilerVersion").asText(),
              root.path("solcVersion").asText(),
              root.path("compiler").path("version").asText(),
              root.path("metadata").path("compiler").path("version").asText());
      for (String candidate : candidates) {
        if (candidate != null && !candidate.isBlank()) {
          return candidate;
        }
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid JSON Standard Input file.", ex);
    }
    throw new IllegalArgumentException(
        "Unable to determine compiler version from JSON Standard Input. "
            + "Add a compiler version field such as compiler.version or compiler_version.");
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
