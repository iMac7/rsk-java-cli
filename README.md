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
Verify contract via Blockscout Standard JSON Input API.
Example:

```powershell
evm-cli verify --json .\standard-input.json --name MyContract --address 0x1234... --compiler-version v0.8.17+commit.8df45f5f
evm-cli verify --testnet --json .\standard-input.json --name MyContract --address 0x1234... --compiler-version v0.8.17+commit.8df45f5f --autodetect-constructor-args false --constructor-args 0xabcdef
```

`contract`  
Interactively call verified read-only contract functions.
Example:

```powershell
evm-cli contract --address 0x1234...
evm-cli contract --address 0x1234... --testnet
```

`bridge`  
Interact with the Rootstock bridge contract (`0x0000000000000000000000000000000001000006`) on mainnet/testnet.  
Lists and allows calling both read and write functions from verified ABI.
Example:

```powershell
evm-cli bridge
evm-cli bridge --testnet
evm-cli bridge --wallet alice --testnet
```

`history`  
Alchemy asset transfer history via `alchemy_getAssetTransfers`.
Example:

```powershell
evm-cli history --apikey yourKey --to-address 0x5c43B1eD97e52d009611D89b74fA829FE4ac56b1 --category erc721,erc1155 --maxcount 0x64 --order desc
evm-cli history --testnet --apikey yourKey --pagekey b48d6463-6903-4970-a3c1-630aaf4b3b53
```

`batch-transfer`  
Send multiple RBTC transfers in one run (interactive or JSON file).  
`to` supports either wallet addresses or RNS names.
Example:

```powershell
evm-cli batch-transfer --interactive
evm-cli batch-transfer --testnet --interactive
evm-cli batch-transfer --file .\batch.json
evm-cli batch-transfer --testnet --file .\batch.json
```

`transaction`  
Transaction builder placeholder.
Example:

```powershell
evm-cli transaction
```

`simulate`  
Sinulation for RBTC or ERC20 transfers.
Example:

```powershell
evm-cli simulate --address 0xRecipientAddress --value 0.001
evm-cli simulate --testnet --address 0xRecipientAddress --value 0.001
evm-cli simulate --address 0xRecipientAddress --value 0.001 --gas-limit 21000 --gas-price 0.00000006
evm-cli simulate --token 0xTokenAddress --address 0xRecipientAddress --value 10
evm-cli simulate --testnet --wallet myWallet --token 0xTokenAddress --address 0xRecipientAddress --value 10
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
