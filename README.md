CMD support
balance


TODO
check custom chain support
wallet with rns
rns?
//peter.rsk resolves

TODO?
wallet - address book





# java cli for evm compatible chains


## Prerequisites

- Java 25 installed
- Windows PowerShell or `cmd.exe`
- Gradle wrapper is included (`gradlew`, `gradlew.bat`)

## Build
Compile and run tests for all modules:

```powershell
.\gradlew.bat build
```

## Run
Run directly from source
windows

```powershell
.\evm-cli.ps1 --help
.\evm-cli.ps1 balance --help
.\evm-cli.ps1 wallet list
```

`cmd.exe` equivalent

```bat
.\evm-cli.bat --help
.\evm-cli.bat transfer --help
```

## CLI Syntax

```bash
evm-cli [--json] <command> [options]
```

Global options:

- `--json`: structured JSON output mode (currently stub)
- `-h|--help`: command help
- `-V|--version`: version

## Supported Commands and Subcommands

`wallet`  
Wallet management

list of available commands
```powershell
./evm-cli wallet --help
```

`wallet create`  
Create an encrypted wallet with name and password.

```powershell
evm-cli wallet create wallet1
```

`wallet import`  
Import wallet from private key (prompts for password).

```powershell
evm-cli wallet import wallet1 --privkey 0xabc123...
```

`wallet list`  
List wallets.

```powershell
evm-cli wallet list
```

`wallet active`  
Show active wallet name and address.

```powershell
evm-cli wallet active
```

`wallet dump`  
Reveal wallet private key (prompts for password). If wallet name is omitted, active wallet is used.

```powershell
evm-cli wallet dump alice
evm-cli wallet dump
```

`wallet switch`  
Switch active wallet.
Example:

```powershell
evm-cli wallet switch alice
```

`wallet rename`  
Rename wallet.
Example:

```powershell
evm-cli wallet rename alice alice-main
```

`wallet delete`  
Delete wallet.
Example:

```powershell
evm-cli wallet delete alice-main
```

`wallet backup`  
Wallet backup TUI placeholder.
Example:

```powershell
evm-cli wallet backup
```

`wallet address-book`  
Address book TUI placeholder.
Example:

```powershell
evm-cli wallet address-book
```

`config`

```powershell
evm-cli config
```

`balance`  
Get native balance by wallet name or address.  
Uses active wallet by default when no target is provided.  
`--address` accepts either a hex address or an RNS name.  
Optional network selector: `--mainnet` or `--testnet` or `--chain <name>`.  
If not specified, defaults to RSK testnet.
Examples:

```powershell
evm-cli balance
evm-cli balance --wallet alice --mainnet
evm-cli balance --address 0x1234... --chain rskTestnet
```

`transfer`  
Send native transfer.  
Requires `--value` and one of `--address` or `--rns`.  
If `--wallet` is omitted, active wallet is used.  
`--address` (and `--rns`) supports RNS resolution; missing names fail with an error.  
Optional: `--token`, `--gas-limit`, `--gas-price`, `--priority-fee`, `--data`, `-i|--interactive`, network selector.  
Attestation options: `--attest-transfer`, `--attest-reason` (stores transfer metadata on-chain as a signed data transaction).
Example:

```powershell
evm-cli transfer --address 0x1234... --value 0.001
evm-cli transfer --rns testing.rsk --value 0.001
evm-cli transfer --testnet --token 0x32Cd6c5831531F96f57d1faf4DDdf0222c4Af8AB --address 0x8A0d290b2EE35eFde47810CA8fF057e109e4190B --value 0.1
evm-cli transfer --testnet -i
evm-cli transfer --address 0x1234... --value 0.001 --attest-transfer --attest-reason "Invoice #42"
```

`tx`  
Get transaction status, or start confirmation monitor.
Example:

```powershell
evm-cli tx --txid 0xdeadbeef... --mainnet
evm-cli tx --txid 0xdeadbeef... --monitor --confirmations 3 --mainnet
```

`monitor`  
List/stop/start monitor sessions.
Examples:

```powershell
evm-cli monitor --list
evm-cli monitor --stop 123e4567-e89b-12d3-a456-426614174000
evm-cli monitor --tx 0xdeadbeef... --confirmations 2
evm-cli monitor 123e4567-e89b-12d3-a456-426614174000
```

`resolve`  
Resolve RNS names and reverse-resolve addresses.
Example:

```powershell
evm-cli resolve alice.rsk
evm-cli resolve 0x1234... --reverse
```

`deploy`  
Deploy a smart contract from ABI + bytecode, with optional constructor args.
Example:

```powershell
evm-cli deploy --abi .\My.abi.json --bytecode .\My.bin --args arg1 arg2
evm-cli deploy --testnet --abi .\My.abi.json --bytecode .\My.bin --args arg1 arg2
evm-cli deploy --wallet alice --abi .\My.abi.json --bytecode .\My.bin --args arg1 arg2
```

`verify`  
Contract verification placeholder.
Example:

```powershell
evm-cli verify --json .\verify.json --name MyContract --address 0x1234...
```

`contract`  
Interactive contract mode placeholder.
Example:

```powershell
evm-cli contract --address 0x1234...
```

`bridge`  
Bridge flow placeholder.
Example:

```powershell
evm-cli bridge --wallet alice
```

`history`  
History API placeholder.
Example:

```powershell
evm-cli history --apiKey yourKey --number 10
```

`batch-transfer`  
Batch transfer builder placeholder.
Example:

```powershell
evm-cli batch-transfer --file .\batch.csv
```

`transaction`  
Transaction builder placeholder.
Example:

```powershell
evm-cli transaction
```

`simulate`  
Simulation builder placeholder.
Example:

```powershell
evm-cli simulate
```

## Useful Help Commands

```powershell
evm-cli --help
evm-cli <command> --help
evm-cli --version
```

## Data Directory

Runtime files are written to:

- `~/.evm-cli/config.json`
- `~/.evm-cli/wallets.json`
- `~/.evm-cli/wallets/*.json`
- `~/.evm-cli/sessions.json`
