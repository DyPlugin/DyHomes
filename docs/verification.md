# Verification record: 1.0.0-BETA

Checked on 2026-10-25 before the first release.

## Automated checks

`gradle clean test build` with Gradle 9.1.0 passed **30 tests, zero failures**. They cover:

- **Storage:** homes surviving a reload, case-insensitive names, moving and deleting, an unreadable file never being overwritten, skipped invalid entries, and warps and spawn persistence.
- **Safety check:** walls, lava, magma, short and long falls, slabs, water and the world height.
- **Ranks:** the highest limit, unlimited, denied nodes, and the lowest cooldown.
- **EssentialsX import:** both file formats, broken entries and broken files.
- **Message files:** every language has the same keys as English, every message the code sends exists, the default config is valid and bad values are rejected, and `plugin.yml` declares every command and Folia.
- **Teleport flow:** warmup, per-kind cooldown, cancellation, unsafe destinations and `force`, DyClaim results, price and refund, and a missing world.
- **Commands:** limit, moving a home, invalid names, claims, cancelled events, disabled worlds, default home, admin permission, deleting, and per-warp permissions.

Java release 17.

## Live checks

Candidate: `DyHomes-1.0.0-BETA.jar`, 151164 bytes, SHA-256 `D5F053CFDE65511B284686CAD62F7FB28AB99504FAA26F8734BD99135D143595`. The same file was used on every server below. Each server was disposable, loopback-only and driven by real offline clients without OP unless stated; bStats was disabled on all of them.

### Purpur 1.20.4

Java 21, with LuckPerms 5.5.71, PlaceholderAPI 2.12.3, Vault 1.7.3, EssentialsX 2.21.2 and DyClaim 1.1.0. **28/28 checks passed:**

- **Import:** EssentialsX homes in the current and the old file format, and a warp, were imported. A second import changed nothing.
- **Homes and warmup:**
  - `/sethome` and `/home` worked, with the warmup.
  - An imported home with an unknown world UID was found by its world name.
  - Walking, or being teleported by another command, cancelled the warmup.
- **Limits:** the default limit of 3 stopped a fourth home; `dyhomes.limit.10` through LuckPerms raised it.
- **Safety:** a walled-in home was refused with the `force` hint, and `force` went anyway.
- **DyClaim:**
  - An untrusted player could not set a home in another player's claim.
  - After `/claim trust` they could.
  - After `/claim untrust` the home was kept but refused.
- **Warps and spawn:**
  - `/setwarp`, `/warp` (case-insensitive) and the clickable warp list worked.
  - `/setspawn` and `/spawn` worked, and a new player was sent to the spawn.
- **Economy:** a price of 25 was charged through Vault and EssentialsX, and the home cooldown applied.
- **Placeholders:** PlaceholderAPI returned count, limit, remaining and warps.
- **Language:** Turkish messages and `/ev` worked.
- **Admin:** `/delhome` removed the home from its file, and other players' homes needed `dyhomes.admin.others`.
- **Command ownership:** with EssentialsX installed, plain `/homes` was answered by DyHomes.
- No DyHomes errors were logged.

### Folia 1.20.4

Java 21, with DyClaim 1.1.0-Folia. Destinations were placed 4000–8000 blocks apart so they sat in other regions. **11/11 checks passed:**

- DyHomes detected Folia.
- `/sethome` worked on a region thread.
- `/home` checked a destination in another region and teleported there.
- Walking cancelled the warmup.
- `/warp` and `/spawn` worked across regions.
- A new player was sent to the spawn.
- A killed player without a bed respawned at the spawn.
- The safety check read blocks in another region.
- `/dyhomes reload` worked.
- No DyHomes errors were logged.

### Spigot 26.3

Build 4663, Java 25, no Adventure. **7/7 checks passed:**

- A MiniMessage tag was shown as written while `&` codes applied.
- `/home` loaded a far chunk and teleported.
- `/setwarp`, `/warp` and `/setspawn` worked.
- A killed player respawned at the spawn.
- German messages were shown.
- No DyHomes errors were logged.

### Paper 26.2

Build 129, Java 25. **7/7 checks passed:**

- A MiniMessage tag was applied.
- `/home` loaded a far chunk and teleported asynchronously.
- `/setwarp`, `/warp` and `/setspawn` worked.
- A killed player respawned at the spawn.
- German messages were shown.
- No DyHomes errors were logged.

## Not covered

The following were not tested:

- Bukkit without Spigot.
- Every Minecraft version between 1.20.4 and 26.3.
- Folia 26.x.
- Geyser/Floodgate clients.
- High player counts.
- HuskHomes data (not imported in this release).

The release listing (1.20.x–26.x) is broader than this live matrix.
