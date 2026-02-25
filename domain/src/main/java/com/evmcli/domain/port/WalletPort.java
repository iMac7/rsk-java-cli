package com.evmcli.domain.port;

import com.evmcli.domain.model.WalletMetadata;
import java.util.List;
import java.util.Optional;

public interface WalletPort {
  WalletMetadata createWallet(String name, char[] password);

  WalletMetadata importWallet(String name, String privateKeyHex, char[] password);

  List<WalletMetadata> listWallets();

  Optional<WalletMetadata> findByName(String name);

  Optional<WalletMetadata> getActiveWallet();

  void switchActiveWallet(String name);

  void renameWallet(String oldName, String newName);

  void deleteWallet(String name);
}
