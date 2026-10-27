package dev.dyhomes.teleport;

import dev.dyhomes.economy.EconomyService;

/** Where a teleport goes. Each kind has its own cooldown and price. */
public enum TeleportKind {
    HOME("home", EconomyService.Action.HOME),
    WARP("warp", EconomyService.Action.WARP),
    SPAWN("spawn", EconomyService.Action.SPAWN),
    BACK("back", EconomyService.Action.BACK);

    private final String key;
    private final EconomyService.Action action;

    TeleportKind(String key, EconomyService.Action action) {
        this.key = key;
        this.action = action;
    }

    /** The key under cooldown.* and economy.prices.*. */
    public String key() {
        return key;
    }

    public EconomyService.Action action() {
        return action;
    }
}
