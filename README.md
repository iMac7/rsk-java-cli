## Available Commands

| Command | Subcommands      | What the subcmd does |
| --- |------------------| --- |
| `wallet` | `create`         | Create a wallet. |
|  | `import`         | Import a wallet from a private key. |
|  | `list`           | List saved wallets. |
|  | `active`         | Show the active wallet. |
|  | `dump`           | Reveal a wallet private key. |
|  | `switch`         | Switch the active wallet. |
|  | `rename`         | Rename a wallet. |
|  | `backup`         | Back up wallet data. |
|  | `delete`         | Delete a wallet. |
| `config` | `-`              | Open the interactive config UI. |
| `balance` | `-`              | Get native or token balance for a wallet, address, or RNS name. |
| `batch-transfer` | `interactive` 🔒 | Enter multiple transfers interactively. |
|  | `file` 🔒        | Execute batch transfers from a file. |
| `bridge` | `read` 🔒        | Call read-only bridge contract functions. |
|  | `write` 🔒       | Execute write bridge contract functions. |
| `contract` | `address` 🔒     | Interact with verified contract read functions. |
| `deploy` | `-`              | Deploy a contract from ABI and bytecode, or from the bundled example. |
| `history` | `-`              | Query transaction history through the History API. |
| `resolve` | `forward`        | Resolve an RNS name to an address. |
|  | `reverse`        | Resolve an address back to an RNS name. |
| `simulate` | `-`              | Simulate an RBTC or ERC20 transfer before sending it. |
| `transfer` | `single`         | Send one RBTC or ERC20 transfer. |
|  | `interactive`    | Enter and send transfers interactively. |
| `transaction` | `simple` 🔒      | Create a simple RBTC or token transfer. |
|  | `advanced` 🔒    | Create a transfer with custom gas settings. |
|  | `raw` 🔒         | Create a transaction with custom data. |
| `tx` | `status`         | Check transaction receipt and status by transaction hash. |
|  | `monitor`        | Keep monitoring a transaction until it reaches the requested confirmations. |
| `verify` | `json` 🔒        | Verify a contract using a JSON Standard Input file. |
|  | `example` 🔒     | Verify a contract using the bundled example JSON. |

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

Wallets and config are stored in:
`~/.rsk-java-cli`
`[home-dir]/.rsk-java-cli`

For backup wallet command, use forward slashes for path in case of failed path arg.
