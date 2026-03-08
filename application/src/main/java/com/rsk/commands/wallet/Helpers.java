package com.rsk.commands.wallet;

import com.evmcli.application.WalletService;
import com.evmcli.domain.model.WalletMetadata;
import com.evmcli.infrastructure.storage.JsonWalletRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class Helpers {
  private final AddressBookStore addressBookStore;
  private final WalletService walletService;

  public Helpers(WalletService walletService, AddressBookStore addressBookStore) {
    this.walletService = walletService;
    this.addressBookStore = addressBookStore;
  }

  public static Helpers defaultHelpers() {
    Path homeDir = Path.of(System.getProperty("user.home"), ".evm-cli");
    JsonWalletRepository walletRepository = new JsonWalletRepository(homeDir);
    WalletService walletService = new WalletService(walletRepository, walletRepository);
    AddressBookStore addressBookStore = new AddressBookStore(homeDir.resolve("wallets.json"));
    return new Helpers(walletService, addressBookStore);
  }

  public WalletMetadata createWallet(String walletName, char[] password) {
    return preserveAddressBook(() -> walletService.create(walletName, password));
  }

  public WalletMetadata importWallet(String walletName, String privateKeyHex, char[] password) {
    return preserveAddressBook(() -> walletService.importPrivateKey(walletName, privateKeyHex, password));
  }

  public List<WalletMetadata> listWallets() {
    return withWalletRegistryAccess(walletService::list);
  }

  public Optional<WalletMetadata> activeWallet() {
    return withWalletRegistryAccess(walletService::active);
  }

  public void switchWallet(String walletName) {
    preserveAddressBook(() -> walletService.switchActive(walletName));
  }

  public void renameWallet(String walletName, String newName) {
    preserveAddressBook(() -> walletService.rename(walletName, newName));
  }

  public void deleteWallet(String walletName) {
    preserveAddressBook(() -> walletService.delete(walletName));
  }

  public String dumpPrivateKey(String walletName, char[] password) {
    return withWalletRegistryAccess(() -> walletService.dumpPrivateKey(walletName, password));
  }

  public WalletMetadata requireWallet(String walletName) {
    return listWallets().stream()
        .filter(wallet -> wallet.name().equals(walletName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletName));
  }

  public Path backupWallet(String walletName, String targetPathInput) {
    WalletMetadata wallet = requireWallet(walletName);
    Path targetDirectory = resolveBackupDirectory(targetPathInput);
    Path targetPath = targetDirectory.resolve(wallet.name() + "_backup.json");

    try {
      Files.copy(
          Path.of(wallet.keystorePath()), targetPath, StandardCopyOption.REPLACE_EXISTING);
      return targetPath.toAbsolutePath().normalize();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to back up wallet " + walletName, ex);
    }
  }

  public Path resolveBackupDirectory(String targetPathInput) {
    if (targetPathInput == null || targetPathInput.isBlank()) {
      throw new IllegalArgumentException("Backup path is required.");
    }

    String sanitizedPath = stripWrappingQuotes(targetPathInput.trim());
    if (!looksLikeWindowsAbsolutePath(sanitizedPath)) {
      throw new IllegalArgumentException("Backup path must be an absolute directory path.");
    }
    Path resolvedPath = Path.of(sanitizedPath).normalize();
    if (!Files.exists(resolvedPath) || !Files.isDirectory(resolvedPath)) {
      throw new IllegalArgumentException("Backup path must point to an existing directory.");
    }
    return resolvedPath;
  }

  public Map<String, String> listAddressBook() {
    return addressBookStore.listEntries();
  }

  public void addAddressBookEntry(String label, String address) {
    addressBookStore.addEntry(label, address);
  }

  public void updateAddressBookEntry(String label, String address) {
    addressBookStore.updateEntry(label, address);
  }

  public void deleteAddressBookEntry(String label) {
    addressBookStore.deleteEntry(label);
  }

  private <T> T preserveAddressBook(Supplier<T> walletAction) {
    Map<String, String> addressBook = addressBookStore.listEntries();
    boolean hadAddressBook = addressBookStore.hasAddressBook();
    if (hadAddressBook) {
      addressBookStore.removeAddressBook();
    }

    try {
      return walletAction.get();
    } finally {
      if (hadAddressBook || !addressBook.isEmpty()) {
        addressBookStore.overwrite(addressBook);
      }
    }
  }

  private void preserveAddressBook(Runnable walletAction) {
    Map<String, String> addressBook = addressBookStore.listEntries();
    boolean hadAddressBook = addressBookStore.hasAddressBook();
    if (hadAddressBook) {
      addressBookStore.removeAddressBook();
    }

    try {
      walletAction.run();
    } finally {
      if (hadAddressBook || !addressBook.isEmpty()) {
        addressBookStore.overwrite(addressBook);
      }
    }
  }

  private <T> T withWalletRegistryAccess(Supplier<T> walletAction) {
    Map<String, String> addressBook = addressBookStore.listEntries();
    boolean hadAddressBook = addressBookStore.hasAddressBook();
    if (hadAddressBook) {
      addressBookStore.removeAddressBook();
    }

    try {
      return walletAction.get();
    } finally {
      if (hadAddressBook || !addressBook.isEmpty()) {
        addressBookStore.overwrite(addressBook);
      }
    }
  }

  private static String stripWrappingQuotes(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  private static boolean looksLikeWindowsAbsolutePath(String value) {
    return value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\");
  }
}
