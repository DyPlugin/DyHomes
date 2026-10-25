# DyHomes 1.0.0-BETA design

DyHomes is the fourth DyPlugin plugin: homes, warps and spawn in one light, command-based plugin. It follows the DyRTP layout and conventions so the family feels the same to players and server owners.

## Scope

- **Homes.** `/home [name]`, `/homes`, `/sethome [name]`, `/delhome <name>`. A home without a name is `home`. Admins reach other players' homes with `/home <player>:<name>` and `/homes <player>`.
- **Warps.** `/warp [name]`, `/warps`, `/setwarp <name>`, `/delwarp <name>`. Optional per-warp permission `dyhomes.warp.<name>`.
- **Spawn.** `/spawn`, `/setspawn`. Optional teleport to spawn on first join and on respawn without a bed or anchor.
- **Teleport flow.** Warmup that cancels on movement or damage, cooldown per kind (home, warp, spawn), optional price per kind through Vault, and a safety check of the destination. An unsafe destination is refused; `/home <name> force` goes anyway.
- **Ranks.** `dyhomes.limit.<n>` sets the home limit (highest wins, `dyhomes.limit.unlimited` removes it). `dyhomes.cooldown.<seconds>` sets the cooldown (lowest wins).
- **DyClaim.** Setting a home inside someone else's claim needs the claim's `build` right; teleporting to a home inside someone else's claim needs its `teleport` right. Both rights are configurable. Losing trust makes such homes unusable without deleting them. If DyClaim cannot answer, the action is refused.
- **Import.** `/dyhomes admin import essentials` copies homes, warps and spawn from EssentialsX files. Existing DyHomes entries are never overwritten.
- **Languages.** English, Turkish, German, Spanish and Russian, picked like DyRTP (saved choice, client locale, or one server-wide language). MiniMessage on Paper and Purpur, `&` codes everywhere.
- **PlaceholderAPI.** `%dyhomes_count%`, `%dyhomes_limit%`, `%dyhomes_remaining%`, `%dyhomes_names%`, `%dyhomes_cooldown%`, `%dyhomes_cooldown_formatted%`, `%dyhomes_warps%`.
- **Developer API.** `DyHomesAPI` reads homes, warps, spawn and limits. Events: `HomeSetEvent` and `HomeDeleteEvent` (cancellable), `DyHomesTeleportEvent` (cancellable, before the teleport).
- **Server builds.** One JAR for Bukkit, Spigot, Paper, Purpur and Folia. Folia uses the region and entity schedulers; Paper and Folia teleport asynchronously.
- **bStats** with its own plugin ID, off with `metrics.enabled: false`. Modrinth update check like DyRTP.

Not in 1.0.0-BETA: `/back`, public homes, home invitations, GUI, HuskHomes import (its database needs a real installation to test against).

## Storage

- `homes/<uuid>.yml`: one file per player, written atomically on each change. A broken file is never overwritten.
- `warps.yml`, `spawn.yml`, `players.yml` (language choice): same atomic writes.
- Locations keep the world name and UID. A home in a missing world is listed but cannot be used.

## Code layout (`dev.dyhomes`)

- `DyHomes`: lifecycle, config upgrades, message defaults, PlaceholderAPI, bStats.
- `ConfigValidator`: staged validation before any reload.
- `model`: `SavedLocation`, `Home`, `Warp`.
- `storage`: `HomeStore`, `WarpStore`, `SpawnStore`, `PlayerDataStore`.
- `teleport`: `TeleportService` (warmup, cooldown, price, events), `TeleportKind`, `SafetyCheck`.
- `rank/RankRules`, `economy/EconomyService`, `claim/ClaimAccess` (DyClaim through reflection), `importer/EssentialsImporter`.
- `command`: one class per command family, localized subcommands and TAB.
- `scheduler/Scheduler`: Bukkit or Folia scheduling.
- `lang`, `util`, `hook/PlaceholderHook`, `api`, `update/ModrinthUpdateChecker`.

## Verification

Unit tests with Mockito like DyRTP, then a live check on disposable Purpur and Spigot servers with a real client.
