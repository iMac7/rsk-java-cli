package com.rsk.commands.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

  private AddressBookStore addressBook() {
    return new AddressBookStore(tempDir.resolve("wallets.json"));
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
}
