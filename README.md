## Available Commands

| Command | Subcommands | What the subcmd does |
| --- | --- | --- |
| `wallet` | `create` | Create a wallet. |
|  | `import` | Import a wallet from a private key. |
|  | `list` | List saved wallets. |
|  | `active` | Show the active wallet. |
|  | `dump` | Reveal a wallet private key. |
|  | `switch` | Switch the active wallet. |
|  | `rename` | Rename a wallet. |
|  | `backup` | Back up wallet data. |
|  | `delete` | Delete a wallet. |
| `config` | `-` | Open the interactive config UI. |
| `balance` | `--wallet <name>` | Check the balance for a wallet name. |
|  | `--address <address>` | Check the balance for an address. |
|  | `--rns <name>` | Check the balance for an RNS name. |
|  | `--token <symbol\|address>` | Check a token balance instead of the native balance. |
|  | `--mainnet` | Use rootstock mainnet. |
|  | `--testnet` | Use rootstock testnet. |
|  | `--chain <name>` | Use a configured chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
| `batch-transfer` | `-f, --file <path>` 🔒 | Execute transactions from a file. |
|  | `-i, --interactive` 🔒 | Execute interactively and input transactions. |
|  | `--rns` | Enable RNS domain resolution for recipient addresses. |
|  | `-t, --testnet` | Execute on the testnet. |
| `bridge` | `--chain <name>` | Use a config chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--gas-limit <gasLimit>` | Set the gas limit override for write calls. |
|  | `--gas-price <gasPrice>` | Set the gas price override in wei for write calls. |
|  | `--mainnet` | Use rootstock mainnet. |
|  | `--testnet` | Use rootstock testnet. |
|  | `--value <value>` | Set the RBTC value for payable write calls. |
|  | `--wallet <wallet>` | Set the wallet name for write calls. |
| `contract` | `-a, --address <address>` 🔒 | Set the address of a verified contract. |
|  | `-t, --testnet` | Use the testnet for contract interaction. |
| `deploy` | `--abi <path>` | Set the path to the ABI file. |
|  | `--args [<constructorArgs>...]` | Provide constructor arguments. |
|  | `--bytecode <path>` | Set the path to the bytecode file. |
|  | `--example` | Use the bundled Owner example contract. |
|  | `-t, --testnet` | Deploy on the testnet. |
|  | `--wallet <wallet>` | Set the wallet name. |
| `history` | `--apikey <apiKey>` | Set the Alchemy API key. |
|  | `--category <category>` | Set comma-separated transfer categories. |
|  | `--chain <name>` | Use a config chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--contract-addresses <contractAddresses>` | Set comma-separated contract addresses. |
|  | `--exclude-zero-value <excludeZeroValue>` | Control whether zero-value transfers are excluded. |
|  | `--from-address <fromAddress>` | Filter by sender address. |
|  | `--from-block <fromBlock>` | Set the starting block. |
|  | `--mainnet` | Use rootstock mainnet. |
|  | `--maxcount <maxCount>` | Set the max count value. |
|  | `--number <number>` | Use an alias for max count. |
|  | `--order <order>` | Set the order to `asc` or `desc`. |
|  | `--pagekey <pageKey>` | Set the pagination key. |
|  | `--testnet` | Use rootstock testnet. |
|  | `--to-address <toAddress>` | Filter by recipient address. |
|  | `--to-block <toBlock>` | Set the ending block. |
| `resolve` | `<value>` 🔒 | Set the RNS name or address to resolve. |
|  | `--chain <name>` | Use a config chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--mainnet` | Use rootstock mainnet. |
|  | `--reverse` | Resolve an address to an RNS name. |
|  | `--testnet` | Use rootstock testnet. |
| `simulate` | `-a, --address <address>` | Set the recipient address or RNS. |
|  | `--chain <name>` | Use a config chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--data <data>` | Set custom transaction data in hex format. |
|  | `--gas-limit <gasLimit>` | Set a custom gas limit override. |
|  | `--gas-price <gasPriceRbtc>` | Set a custom gas price in RBTC. |
|  | `--mainnet` | Use rootstock mainnet. |
|  | `--testnet` | Use rootstock testnet. |
|  | `--token <token>` | Set the ERC20 token contract address. |
|  | `--value <value>` | Set the transfer amount. |
|  | `-w, --wallet <wallet>` | Set the wallet name. |
| `transfer` | `-a, --address <address>` | Set the recipient address. |
|  | `--chain <name>` | Use a configured chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--data <data>` | Include custom transaction data in hex format. |
|  | `--gas-limit <limit>` | Set a custom gas limit. |
|  | `--gas-price <price>` | Set a custom gas price in RBTC. |
|  | `-i, --interactive` | Execute interactively and input transactions. |
|  | `--mainnet` | Transfer on the mainnet. |
|  | `--rns <domain>` | Set the recipient as an RNS domain. |
|  | `-t, --testnet` | Transfer on the testnet. |
|  | `--token <address>` | Set the ERC20 token contract address. |
|  | `--value <value>` | Set the amount to transfer. |
|  | `--wallet <wallet>` | Set the wallet name. |
| `transaction` | `--chain <name>` | Use a configured chain. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--mainnet` | Use mainnet. |
|  | `-t, --testnet` | Use testnet. |
|  | `--wallet <wallet>` | Set the wallet name. |
| `tx` | `--chain <name>` | Use a config chain key. |
|  | `--chainurl <url>` | Use an explicit RPC URL. |
|  | `--confirmations <confirmations>` | Set the number of confirmations for monitoring. |
|  | `-id, --txid <txid>` 🔒 | Set the transaction ID. |
|  | `--mainnet` | Check the transaction on the mainnet. |
|  | `--monitor` | Keep monitoring the transaction until confirmation. |
|  | `-t, --testnet` | Check the transaction status on the testnet. |
| `verify` | `-a, --address <address>` 🔒 | Set the address of the deployed contract. |
|  | `--example` | Use the bundled Owner example verification JSON. |
|  | `--json <path>` | Set the path to the JSON Standard Input file. |
|  | `--name <name>` 🔒 | Set the contract name. |

`🔒 = required or alternative for required`

## Build
> Note: Requires JDK 25 installed
```bash
$ java --version
openjdk 25.0.2 2026-01-20
```

macOS/Linux:
`./gradlew :application:build`

make sure `gradlew` is executable
```bash
chmod +x gradlew
```

Windows:
`.\gradlew.bat :application:build`

### Run
Run from the repo root.

macOS/Linux wrapper:
`./rsk-java-cli`

Windows CMD wrapper:
`rsk-java-cli.bat`

Windows PowerShell wrapper:
`.\rsk-java-cli.ps1`

Run from source with Gradle:
macOS/Linux:
`./gradlew :application:runCli`

Windows:
`.\gradlew.bat :application:runCli`

pass CLI arguments after the command
`./rsk-java-cli wallet --help`
`.\rsk-java-cli.ps1 wallet --help`
`.\gradlew.bat :application:runCli --args="wallet --help"`

Wallets and config are stored in
`~/.rsk-java-cli`
where `~` is your home directory.

For backup wallet command, use forward slashes for path in case of failed path arg.
