package com.rsk.commands.wallet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsk.utils.Json.ObjectMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AddressBookStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(AddressBookStore.class);
  private static final TypeReference<LinkedHashMap<String, Object>> REGISTRY_TYPE =
      new TypeReference<>() {};

  private final Path registryPath;
  private final ObjectMapper objectMapper;

  AddressBookStore(Path registryPath) {
    this.registryPath = registryPath;
    this.objectMapper = ObjectMapperFactory.create();
  }

  Map<String, String> listEntries() {
    return new LinkedHashMap<>(readAddressBook(loadRegistryDocument()));
  }

  void addEntry(String label, String address) {
    LinkedHashMap<String, Object> registry = loadRegistryDocument();
    LinkedHashMap<String, String> addressBook = readAddressBook(registry);
    if (addressBook.containsKey(label)) {
      throw new IllegalArgumentException("Address label already exists: " + label);
    }
    addressBook.put(label, address);
    registry.put("addressBook", addressBook);
    saveRegistryDocument(registry);
  }

  void updateEntry(String label, String address) {
    LinkedHashMap<String, Object> registry = loadRegistryDocument();
    LinkedHashMap<String, String> addressBook = readAddressBook(registry);
    if (!addressBook.containsKey(label)) {
      throw new IllegalArgumentException("Address label not found: " + label);
    }
    addressBook.put(label, address);
    registry.put("addressBook", addressBook);
    saveRegistryDocument(registry);
  }

  void deleteEntry(String label) {
    LinkedHashMap<String, Object> registry = loadRegistryDocument();
    LinkedHashMap<String, String> addressBook = readAddressBook(registry);
    if (addressBook.remove(label) == null) {
      throw new IllegalArgumentException("Address label not found: " + label);
    }
    registry.put("addressBook", addressBook);
    saveRegistryDocument(registry);
  }

  void overwrite(Map<String, String> addressBookEntries) {
    LinkedHashMap<String, Object> registry = loadRegistryDocument();
    registry.put("addressBook", new LinkedHashMap<>(addressBookEntries));
    saveRegistryDocument(registry);
  }

  boolean hasAddressBook() {
    LinkedHashMap<String, Object> registry = loadRegistryDocument();
    return registry.containsKey("addressBook");
  }

  void removeAddressBook() {
    LinkedHashMap<String, Object> registry = loadRegistryDocument();
    registry.remove("addressBook");
    saveRegistryDocument(registry);
  }

  private LinkedHashMap<String, Object> loadRegistryDocument() {
    try {
      if (!Files.exists(registryPath)) {
        return defaultRegistryDocument();
      }

      LinkedHashMap<String, Object> registry =
          objectMapper.readValue(registryPath.toFile(), REGISTRY_TYPE);
      if (registry == null) {
        return defaultRegistryDocument();
      }

      registry.putIfAbsent("wallets", new ArrayList<>());
      return registry;
    } catch (IOException ex) {
      LOGGER.error("Unable to load wallet registry document from {}", registryPath, ex);
      throw new IllegalStateException("Unable to load wallet registry", ex);
    }
  }

  private void saveRegistryDocument(LinkedHashMap<String, Object> registry) {
    try {
      Files.createDirectories(registryPath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), registry);
    } catch (IOException ex) {
      LOGGER.error("Unable to save wallet registry document to {}", registryPath, ex);
      throw new IllegalStateException("Unable to save wallet registry", ex);
    }
  }

  private LinkedHashMap<String, String> readAddressBook(Map<String, Object> registry) {
    LinkedHashMap<String, String> addressBook = new LinkedHashMap<>();
    Object rawAddressBook = registry.get("addressBook");
    if (!(rawAddressBook instanceof Map<?, ?> rawMap)) {
      return addressBook;
    }

    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      addressBook.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
    }
    return addressBook;
  }

  private static LinkedHashMap<String, Object> defaultRegistryDocument() {
    LinkedHashMap<String, Object> registry = new LinkedHashMap<>();
    registry.put("wallets", new ArrayList<>());
    return registry;
  }
}
