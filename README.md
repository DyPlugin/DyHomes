<p align="center"><img src="docs/images/logo.png" alt="DyHomes logo" width="128"></p>

# DyHomes — Homes, Warps and Spawn Made Simple

**Set your homes. Share your warps. Always find your way back.**

DyHomes is a command-based homes plugin for survival, SMP and economy servers. Players set homes and return to them, server owners create warps and a spawn, and everything stays light: no GUI, no database server, one JAR for every server build.

DyHomes combines named homes with rank limits, warps, spawn handling, destination safety checks, DyClaim trust support and optional prices with a familiar command-based workflow. **Version 1.0.0-BETA** is the first public release.

---

## Why DyHomes?

**Homes that respect claims.** With DyClaim 1.1.0 or later, setting a home inside someone else's claim needs that claim's `build` right, and using it needs the `teleport` right. Lose the trust and the home stays but can no longer be used; get it back and it works again.

**Safe arrivals.** Before each teleport DyHomes checks the destination: room to stand, no lava or fire, and ground within a short fall. Builds change after a home is set; an unsafe home is refused with a hint to add `force` if the player really wants to go.

**Ranks without a GUI.** `dyhomes.limit.<n>` sets how many homes a group may have (the highest wins, `dyhomes.limit.unlimited` removes the limit). `dyhomes.cooldown.<seconds>` sets their cooldown (the lowest wins).

**One JAR for every server.** Bukkit, Spigot, Paper, Purpur and Folia. On Folia, homes in other regions are checked and reached on the right region thread; on Paper and Folia teleports are asynchronous.

**Easy switch from EssentialsX.** `/dyhomes import essentials` copies homes, warps and the spawn. Nothing already in DyHomes is replaced, and EssentialsX files are only read.

**Five languages.** English, Turkish, German, Spanish and Russian, picked from the player's Minecraft language or their own choice. Turkish players also get Turkish commands such as `/ev`, `/evler` and `/evayarla`. Messages accept `&` colour codes everywhere and MiniMessage tags on Paper, Purpur and Folia.

---

## Choose your server build

| Server | JAR |
| --- | --- |
| Bukkit, Spigot, Paper, Purpur, Folia | `DyHomes-1.0.0-BETA.jar` |

Java 17 bytecode; use the Java runtime your server needs. Release listing: Minecraft 1.20.x through 26.x. The live checks for this release used Purpur 1.20.4, Folia 1.20.4, Paper 26.2 and Spigot 26.3; see the [verification record](docs/verification.md).

[Downloads](https://github.com/DyPlugin/DyHomes/releases/tag/1.0.0-BETA) · [Changelog](CHANGELOG.md)

## Commands

| Command | Turkish | Purpose |
|---|---|---|
| `/home [name]` | `/ev` | Teleport to a home; without a name, to `home` or your only home |
| `/home <name> force` | `/ev <ad> zorla` | Go even if the destination looks unsafe |
| `/homes` | `/evler` | List your homes; names are clickable |
| `/sethome [name]` | `/evayarla`, `/evkur` | Set a home where you stand, or move an existing one |
| `/delhome <name>` | `/evsil` | Delete a home |
| `/warp [name]` | `/warp` | Teleport to a warp; without a name, list the warps |
| `/warps` | `/warplar` | List the warps |
| `/spawn` | `/spawn` | Teleport to the spawn |
| `/dyhomes language <auto/en/tr/de/es/ru>` | `/dyhomes dil` | Save your language |
| `/setwarp <name>` | `/warpayarla` | Create or move a warp |
| `/delwarp <name>` | `/warpsil` | Delete a warp |
| `/setspawn` | `/spawnayarla` | Set the spawn where you stand |
| `/homes <player>` | `/evler <oyuncu>` | List another player's homes |
| `/home <player>:<name>` | `/ev <oyuncu>:<ad>` | Visit another player's home |
| `/delhome <player>:<name>` | `/evsil <oyuncu>:<ad>` | Delete another player's home |
| `/dyhomes import essentials` | `/dyhomes aktar essentials` | Copy homes, warps and spawn from EssentialsX |
| `/dyhomes reload` | `/dyhomes yenile` | Validate and reload settings and messages |

`/dyh` is short for `/dyhomes`, `/h` for `/home`. Home and warp names use letters of any alphabet, digits, `_` and `-`, up to 32 characters, and ignore case. With EssentialsX installed, plain `/home`, `/homes` and the other commands still reach DyHomes; `/dyhomes:home` always does.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `dyhomes.home` | true | `/home`, `/homes` |
| `dyhomes.sethome` | true | `/sethome`, `/delhome` for your own homes |
| `dyhomes.warp` | true | `/warp`, `/warps` |
| `dyhomes.warp.<name>` | not set | One warp, when `warps.per-warp-permission` is on |
| `dyhomes.spawn` | true | `/spawn` |
| `dyhomes.setwarp`, `dyhomes.delwarp` | op | Manage warps |
| `dyhomes.setspawn` | op | Set the spawn |
| `dyhomes.admin` | op | Reload, import, server language, update notices |
| `dyhomes.admin.others` | op | Other players' homes |
| `dyhomes.limit.<n>` | not set | Home limit; the highest wins |
| `dyhomes.limit.unlimited` | op | No home limit |
| `dyhomes.cooldown.<seconds>` | not set | Cooldown; the lowest wins |
| `dyhomes.bypass.cooldown`, `.warmup`, `.economy` | op | Skip cooldown, warmup or prices |
| `dyhomes.bypass.claims` | op | Set and use homes in any DyClaim claim |
| `dyhomes.bypass.worlds` | op | Set homes in `homes.disabled-worlds` |

## Teleports

Every home, warp and spawn teleport follows the same steps: cooldown, price check, DyClaim access (homes), destination safety, the cancellable `DyHomesTeleportEvent`, then a warmup of `warmup.seconds` (3 by default). Moving, taking damage or being teleported by something else cancels the warmup. The price is taken right before the teleport and refunded if it fails; the cooldown only starts after a successful teleport. Cooldowns and prices are set per kind: `cooldown.home`, `cooldown.warp`, `cooldown.spawn` and `economy.prices.home`, `.warp`, `.spawn`, `.sethome`.

Spawn: `/setspawn` stores the spawn and, with `spawn.set-world-spawn`, also moves the vanilla world spawn. New players go to it (`spawn.on-first-join`), and players without a bed or respawn anchor respawn there (`spawn.on-respawn`). Without `/setspawn`, `/spawn` uses the main world's spawn.

## Placeholders

With PlaceholderAPI: `%dyhomes_count%`, `%dyhomes_limit%`, `%dyhomes_remaining%`, `%dyhomes_names%`, `%dyhomes_cooldown%`, `%dyhomes_cooldown_formatted%` and `%dyhomes_warps%`.

## For developers

`dev.dyhomes.api.DyHomesAPI` reads homes (also of offline players), warps, the spawn and a player's limit. `HomeSetEvent` and `HomeDeleteEvent` are cancellable and fire before a change; `DyHomesTeleportEvent` is cancellable and fires before the warmup. Add `softdepend: [DyHomes]` and check `DyHomesAPI.isAvailable()` first.

## Data and upgrades

Homes are stored in `plugins/DyHomes/homes/<uuid>.yml`, one small file per player; warps in `warps.yml`, the spawn in `spawn.yml` and language choices in `players.yml`. Every file is written atomically. A file that cannot be read is never overwritten: its homes stay read-only and the console says which file to fix. Homes remember their world by UID and name, so a renamed world keeps its homes.

Missing settings and message keys are added on start and reload without touching your edits. An invalid setting rejects the reload and keeps the running settings. DyHomes checks Modrinth every six hours for a newer release and tells administrators; `updates.check: false` turns this off. Anonymous [bStats](https://bstats.org/plugin/bukkit/DyHomes/34939) statistics can be turned off with `metrics.enabled: false`.

## Build

```text
gradle clean build
```

Gradle 9.1 with Java 21 or 25. The JAR is `build/libs/DyHomes-1.0.0-BETA.jar`. Tests use JUnit and Mockito and are not part of the JAR.

## License

DyHomes Source-Available License 1.0, the same terms as DyClaim, DyRTP and DyDiscord: free to use and modify on any server, no paid distribution, and distributed versions must share their source. It is a custom source-available license, not an OSI-approved open-source license. See [LICENSE](LICENSE).

## Get started

1. Put `DyHomes-1.0.0-BETA.jar` in `plugins/` and start the server.
2. Stand where players should arrive and run `/setspawn`; create warps with `/setwarp <name>`.
3. Coming from EssentialsX? Run `/dyhomes import essentials` once.
4. Adjust `config.yml` (limits, warmup, cooldowns, prices) and run `/dyhomes reload`.

## Source and support

[Documentation](https://github.com/DyPlugin/DyHomes#readme) · [Report an issue](https://github.com/DyPlugin/DyHomes/issues) · [License](https://github.com/DyPlugin/DyHomes/blob/main/LICENSE)

**By Claww / DyPlugin.**
