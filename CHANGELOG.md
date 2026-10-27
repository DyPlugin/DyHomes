# DyHomes 1.0.0

The first stable release. It adds public homes, `/back`, importing from HuskHomes, warp markers on web maps and events for warps and public homes. Homes, warps, settings and translations from 1.0.0-BETA are kept.

## Added

- **Public homes.**
  - `/publichome <name>` (Turkish `/evpaylas`) opens a home to everyone or closes it again.
  - `/phomes [player]` (`/acikevler`) lists public homes with clickable names, and `/phome <player:home>` (`/acikev`) visits one.
  - Visiting follows the normal home teleport: warmup, cooldown, price and safety checks. The owner's DyClaim access applies, so a public home in a claim the owner can no longer use stays closed.
  - Settings: `public-homes.enabled` and `public-homes.max-per-player` (3). `dyhomes.bypass.public-limit` removes the limit.
- **/back** (`/geri`) returns to where you last teleported from or died.
  - Settings: `back.enabled`, `on-teleport`, `on-death` and `disabled-worlds`.
  - It has its own `cooldown.back` and `economy.prices.back`.
  - Positions are kept in memory until the player leaves.
- **HuskHomes import.** `/dyhomes import huskhomes` copies homes, public homes and warps from a HuskHomes SQLite database, including custom table names. Existing DyHomes entries are kept, and HuskHomes data is only read.
- **Web maps.** Warps appear as markers on BlueMap, Dynmap and squaremap when one of them is installed. `maps.public-homes` adds public homes. Dynmap and squaremap are not used on Folia.
- **Developer API.** `WarpChangeEvent`, `HomePublicChangeEvent` and `DyHomesAPI.getPublicHomes()`. DyDiscord 1.2.0 can post these events to a log channel.

## Fixed

- An administrator's look at an offline player's homes no longer keeps those homes in memory.
- Clickable lists fall back to plain text on CraftBukkit servers without chat components.
- Help and import texts that were never edited are replaced with the new wording after an upgrade from 1.0.0-BETA. Edited texts are kept.

## Upgrading

Stop the server, replace `DyHomes-1.0.0-BETA.jar` with `DyHomes-1.0.0.jar` and start it again. New settings and messages are added; nothing you changed is replaced.

Full changelog: https://dynastyplugins.com/dyhomes/versions/1.0.0
