package com.evmcli.cli;

import com.evmcli.application.BalanceService;
import com.evmcli.application.MonitorManager;
import com.evmcli.application.TransferService;
import com.evmcli.application.TxService;
import com.evmcli.application.WalletService;
import com.evmcli.domain.port.ConfigPort;
import com.evmcli.domain.port.MonitorSessionPort;
import com.evmcli.domain.port.RpcPort;
import com.evmcli.domain.port.WalletUnlockPort;
import com.evmcli.infrastructure.rpc.Web3jRpcGateway;
import com.evmcli.infrastructure.storage.JsonConfigRepository;
import com.evmcli.infrastructure.storage.JsonMonitorSessionRepository;
import com.evmcli.infrastructure.storage.JsonWalletRepository;
import java.nio.file.Path;

public class CliContext {
  private final ConfigPort configPort;
  private final WalletService walletService;
  private final BalanceService balanceService;
  private final TransferService transferService;
  private final TxService txService;
  private final MonitorManager monitorManager;

  public CliContext(Path homeDir) {
    JsonWalletRepository walletRepository = new JsonWalletRepository(homeDir);
    RpcPort rpcPort = new Web3jRpcGateway();
    this.configPort = new JsonConfigRepository(homeDir);
    this.walletService = new WalletService(walletRepository, walletRepository);
    this.balanceService = new BalanceService(rpcPort);
    this.transferService = new TransferService(rpcPort, (WalletUnlockPort) walletRepository);
    this.txService = new TxService(rpcPort);
    MonitorSessionPort monitorSessionPort = new JsonMonitorSessionRepository(homeDir);
    this.monitorManager = new MonitorManager(monitorSessionPort);
  }

  public ConfigPort configPort() {
    return configPort;
  }

  public WalletService walletService() {
    return walletService;
  }

  public BalanceService balanceService() {
    return balanceService;
  }

  public TransferService transferService() {
    return transferService;
  }

  public TxService txService() {
    return txService;
  }

  public MonitorManager monitorManager() {
    return monitorManager;
  }

  public static Path defaultHome() {
    return Path.of(System.getProperty("user.home"), ".evm-cli");
  }
}
