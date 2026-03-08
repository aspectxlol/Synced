# Synced

[![Modrinth](https://img.shields.io/modrinth/dt/synced?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/project/synced)
[![GitHub Release](https://img.shields.io/github/v/release/aspectxlol/Synced?logo=github)](https://github.com/aspectxlol/Synced/releases)

A Paper plugin that synchronizes player data between teammates in real time. Form a team, toggle what syncs, and play as one.

## Features

- **Inventory Sync** — shared hotbar, armor, and off-hand
- **Health Sync** — take damage together, heal together
- **Hunger Sync** — food level, saturation, and exhaustion
- **XP Sync** — levels and experience points
- **Potion Effects Sync** — active effects propagate across the team
- **Ender Chest Sync** — shared ender chest contents
- **Position Sync** — optional teleportation to teammates
- **Totem of Undying** — configurable sync shielding on totem activation
- **Tablist Colors** — teammates are color-coded in the player list
- **In-game Config GUI** — toggle sync options per-team through a chest UI

All sync types are individually toggleable per team via the GUI or config defaults.

## Commands

| Command | Description |
|---|---|
| `/sync join <player>` | Invite a player or accept their invite |
| `/sync start` | Open the sync configuration GUI |
| `/sync info` | Show team members and toggle status |
| `/sync kick <player>` | Kick a player from the team (leader only) |
| `/sync leave` | Leave your sync team |
| `/sync disband` | Disband the team (leader only) |

## Configuration

```yaml
# config.yml
defaults:
  inventory-sync: true
  health-sync: true
  hunger-sync: true
  xp-sync: true
  potion-effects-sync: true
  ender-chest-sync: true
  position-sync: false

sync-totem-effects: false
```

## Requirements

- Paper 1.21+
- Java 21+

## Building

```sh
./gradlew build
```

The plugin JAR will be in `build/libs/`.

## License

All rights reserved.
