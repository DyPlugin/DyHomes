# DyHomes 1.0.0-BETA

The first public release of DyHomes: homes, warps and spawn in one light plugin for Bukkit, Spigot, Paper, Purpur and Folia, Minecraft 1.20.x through 26.x.

## Added

- **Homes.** `/home`, `/homes`, `/sethome` and `/delhome`.
  - Named homes; without a name, `/sethome` sets `home` and `/home` goes to `home` or your only home.
  - `/sethome` with an existing name moves that home instead of counting as a new one.
  - Clickable home list.
  - Administrators reach other players' homes with `player:home`, also for offline players.
- **Limits per rank.** `homes.default-limit` (3), `dyhomes.limit.<n>` (the highest wins) and `dyhomes.limit.unlimited`. `homes.disabled-worlds` blocks new homes in chosen worlds.
- **Warps.** `/warp`, `/warps`, `/setwarp` and `/delwarp`, with a clickable list. `warps.per-warp-permission` limits each warp to `dyhomes.warp.<name>`.
- **Spawn.**
  - `/spawn` and `/setspawn`.
  - New players are sent to the spawn.
  - Players without a bed or respawn anchor respawn there.
  - Without `/setspawn`, `/spawn` uses the main world's spawn.
- **Teleport flow.**
  - Warmup (3 seconds), cancelled by moving, damage or another teleport.
  - Cooldown and price per kind: home, warp, spawn; price also for `/sethome`.
  - `dyhomes.cooldown.<seconds>` per rank (the lowest wins).
  - The price is taken only right before the teleport and refunded if it fails.
- **Destination safety.**
  - Room to stand, no lava, fire or other hazard, and ground within `safety.max-fall` blocks.
  - An unsafe destination is refused with a hint; adding `force` goes anyway.
  - Slabs, carpets and water at the destination are handled.
- **DyClaim.**
  - With DyClaim 1.1.0 or later, homes in someone else's claim need its `build` right to set and its `teleport` right to use. Both rights are configurable.
  - Losing trust keeps the home but blocks it.
  - If DyClaim cannot answer, the action is refused.
- **EssentialsX import.**
  - `/dyhomes import essentials` copies homes (current and older file formats), warps and the spawn.
  - Existing DyHomes entries are kept.
  - EssentialsX files are only read.
- **Languages.** English, Turkish, German, Spanish and Russian, from the client language or `/dyhomes language`. Turkish command aliases such as `/ev`, `/evler`, `/evayarla`, `/evsil`. MiniMessage tags on Paper, Purpur and Folia; text typed by players is never treated as tags.
- **PlaceholderAPI.** `%dyhomes_count%`, `%dyhomes_limit%`, `%dyhomes_remaining%`, `%dyhomes_names%`, `%dyhomes_cooldown%`, `%dyhomes_cooldown_formatted%` and `%dyhomes_warps%`.
- **Developer API.** `DyHomesAPI` plus the cancellable `HomeSetEvent`, `HomeDeleteEvent` and `DyHomesTeleportEvent`.
- **Folia.** The same JAR runs on Folia with region and entity schedulers; Paper and Folia teleport asynchronously.
- **Storage.**
  - One small YAML file per player, written atomically.
  - Unreadable files are never overwritten.
  - Worlds are remembered by UID and name.
- Modrinth update notices for administrators and optional anonymous bStats statistics.

## License

DyHomes is released under the DyHomes Source-Available License 1.0, the same terms as DyClaim, DyRTP and DyDiscord.
