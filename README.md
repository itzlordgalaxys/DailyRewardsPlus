# DailyRewardsPlus

A configurable daily rewards plugin for Minecraft servers running Spigot.

DailyRewardsPlus gives players a reason to log in regularly through a seven-day reward system, a customizable inventory GUI, player statistics and a leaderboard.

## Features

- **7-day reward system** – Players progress through seven daily rewards.
- **24-hour cooldown** – Each reward has a 24-hour claim cooldown.
- **Customizable rewards** – Configure reward items and console commands in `config.yml`.
- **Inventory GUI** – View available, claimed and locked rewards.
- **Player statistics** – Track total claims and player progress.
- **Leaderboard** – Display the top players by total rewards claimed.
- **Custom messages** – Edit plugin messages in `messages.yml`.
- **Admin commands** – Manage player progress and reload plugin configuration.
- **Debug mode** – Useful for troubleshooting.

## Requirements

- Minecraft server running Spigot or a compatible server.
- Java 17 or newer.
- Spigot API 1.20.4 compatibility.

## Installation

1. Download the plugin JAR.
2. Place it in your server's `plugins` folder.
3. Restart your server.
4. Configure the generated `config.yml` and `messages.yml` files.

## Commands

| Command | Description |
|---|---|
| `/rewards` | Open the daily rewards menu |
| `/dailyreload` | Reload plugin configuration |
| `/dailyskip` | Skip a player's reward days |
| `/dailyreset` | Reset a player's progress |
| `/dailydebug` | Toggle debug mode |
| `/dailyleaderboard` | View the leaderboard |
| `/dailystats` | View player statistics |
| `/dailyhelp` | Show available commands |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `dailyrewards.*` | All plugin permissions | OP |
| `dailyrewards.reload` | Reload configuration | OP |
| `dailyrewards.skip` | Skip player reward days | OP |
| `dailyrewards.reset` | Reset player progress | OP |
| `dailyrewards.debug` | Use debug commands | OP |
| `dailyrewards.leaderboard` | View leaderboard | OP |
| `dailyrewards.stats` | View statistics | OP |
| `dailyrewards.help` | View help | OP |

## Configuration

The plugin uses two main configuration files:

- `config.yml` – Reward definitions, items and reward commands.
- `messages.yml` – Customizable messages displayed by the plugin.

## Building from source

This project uses Maven.

```bash
mvn clean package
```

The compiled JAR will be generated in the `target` directory.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
