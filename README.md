

### Build
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
