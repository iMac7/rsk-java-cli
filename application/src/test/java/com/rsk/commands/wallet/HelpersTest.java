package com.rsk.commands.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HelpersTest {
  @TempDir Path tempDir;

  @Test
  void resolveBackupDirectoryAcceptsUnixStyleAbsolutePaths() {
    Helpers helpers = new Helpers(new NoOpWalletPort(), (walletName, password) -> "", addressBook());

    Path resolved = helpers.resolveBackupDirectory(tempDir.toString().replace('\\', '/'));

    assertThat(resolved).isEqualTo(tempDir.normalize());
  }

  @Test
  void walletCrudOperationsPreserveAddressBookEntries() {
    AddressBookStore addressBookStore = addressBook();
    addressBookStore.addEntry("treasury", "0x1111111111111111111111111111111111111111");
    FakeWalletPort walletPort = new FakeWalletPort(addressBookStore);
    Helpers helpers = new Helpers(walletPort, walletPort, addressBookStore);

    Helpers.WalletMetadata alice = helpers.createWallet("alice", "secret".toCharArray());
    Helpers.WalletMetadata bob =
        helpers.importWallet(
            "bob",
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            "secret".toCharArray());

    assertThat(helpers.listWallets()).extracting(Helpers.WalletMetadata::name).containsExactly("alice", "bob");
    assertThat(helpers.activeWallet()).contains(alice);

    helpers.switchWallet("bob");
    assertThat(helpers.activeWallet()).contains(bob);

    helpers.renameWallet("bob", "carol");
    assertThat(helpers.requireWallet("carol").name()).isEqualTo("carol");
    assertThat(helpers.dumpPrivateKey("carol", "secret".toCharArray()))
        .isEqualTo("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

    helpers.deleteWallet("alice");

    assertThat(helpers.listWallets()).extracting(Helpers.WalletMetadata::name).containsExactly("carol");
    assertThat(helpers.listAddressBook())
        .containsEntry("treasury", "0x1111111111111111111111111111111111111111");
  }

  @Test
  void requireWalletRejectsUnknownWalletName() {
    Helpers helpers = new Helpers(new NoOpWalletPort(), (walletName, password) -> "", addressBook());

    assertThatThrownBy(() -> helpers.requireWallet("missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Wallet not found: missing");
  }

  private AddressBookStore addressBook() {
    return new AddressBookStore(tempDir.resolve("wallets.json"));
  }

  private Helpers.WalletMetadata wallet(String name, String address) {
    Instant timestamp = Instant.parse("2024-01-01T00:00:00Z");
    return new Helpers.WalletMetadata(
        UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),
        name,
        address,
        tempDir.resolve(name + ".json").toString(),
        timestamp,
        timestamp);
  }

  private static final class NoOpWalletPort implements Helpers.WalletPort {
    @Override
    public Helpers.WalletMetadata createWallet(String name, char[] password) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Helpers.WalletMetadata importWallet(String name, String privateKeyHex, char[] password) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Helpers.WalletMetadata> listWallets() {
      return List.of();
    }

    @Override
    public Optional<Helpers.WalletMetadata> findByName(String name) {
      return Optional.empty();
    }

    @Override
    public Optional<Helpers.WalletMetadata> getActiveWallet() {
      return Optional.empty();
    }

    @Override
    public void switchActiveWallet(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void renameWallet(String oldName, String newName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteWallet(String name) {
      throw new UnsupportedOperationException();
    }
  }

  private final class FakeWalletPort implements Helpers.WalletPort, Helpers.WalletUnlockPort {
    private final AddressBookStore addressBookStore;
    private final Map<String, Helpers.WalletMetadata> wallets = new LinkedHashMap<>();
    private final Map<String, String> privateKeys = new LinkedHashMap<>();
    private String activeWalletName;

    private FakeWalletPort(AddressBookStore addressBookStore) {
      this.addressBookStore = addressBookStore;
    }

    @Override
    public Helpers.WalletMetadata createWallet(String name, char[] password) {
      assertAddressBookIsTemporarilyRemoved();
      Helpers.WalletMetadata metadata = wallet(name, hexAddress('1'));
      wallets.put(name, metadata);
      if (activeWalletName == null) {
        activeWalletName = name;
      }
      privateKeys.put(name, "created-" + name);
      return metadata;
    }

    @Override
    public Helpers.WalletMetadata importWallet(String name, String privateKeyHex, char[] password) {
      assertAddressBookIsTemporarilyRemoved();
      Helpers.WalletMetadata metadata = wallet(name, hexAddress('2'));
      wallets.put(name, metadata);
      privateKeys.put(name, privateKeyHex);
      return metadata;
    }

    @Override
    public List<Helpers.WalletMetadata> listWallets() {
      assertAddressBookIsTemporarilyRemoved();
      return new ArrayList<>(wallets.values());
    }

    @Override
    public Optional<Helpers.WalletMetadata> findByName(String name) {
      assertAddressBookIsTemporarilyRemoved();
      return Optional.ofNullable(wallets.get(name));
    }

    @Override
    public Optional<Helpers.WalletMetadata> getActiveWallet() {
      assertAddressBookIsTemporarilyRemoved();
      return Optional.ofNullable(wallets.get(activeWalletName));
    }

    @Override
    public void switchActiveWallet(String name) {
      assertAddressBookIsTemporarilyRemoved();
      activeWalletName = requireExisting(name).name();
    }

    @Override
    public void renameWallet(String oldName, String newName) {
      assertAddressBookIsTemporarilyRemoved();
      Helpers.WalletMetadata existing = requireExisting(oldName);
      wallets.remove(oldName);
      Helpers.WalletMetadata renamed =
          new Helpers.WalletMetadata(
              existing.walletId(),
              newName,
              existing.address(),
              existing.keystorePath(),
              existing.createdAt(),
              existing.lastUsedAt());
      wallets.put(newName, renamed);
      privateKeys.put(newName, privateKeys.remove(oldName));
      if (oldName.equals(activeWalletName)) {
        activeWalletName = newName;
      }
    }

    @Override
    public void deleteWallet(String name) {
      assertAddressBookIsTemporarilyRemoved();
      requireExisting(name);
      wallets.remove(name);
      privateKeys.remove(name);
      if (name.equals(activeWalletName)) {
        activeWalletName = wallets.keySet().stream().findFirst().orElse(null);
      }
    }

    @Override
    public String unlockPrivateKeyHex(String walletName, char[] password) {
      assertAddressBookIsTemporarilyRemoved();
      requireExisting(walletName);
      return privateKeys.get(walletName);
    }

    private Helpers.WalletMetadata requireExisting(String name) {
      Helpers.WalletMetadata wallet = wallets.get(name);
      if (wallet == null) {
        throw new IllegalArgumentException("Wallet not found: " + name);
      }
      return wallet;
    }

    private void assertAddressBookIsTemporarilyRemoved() {
      assertThat(addressBookStore.hasAddressBook()).isFalse();
    }

    private String hexAddress(char digit) {
      return "0x" + String.valueOf(digit).repeat(40);
    }
  }
}
