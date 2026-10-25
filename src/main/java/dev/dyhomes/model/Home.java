package dev.dyhomes.model;

import java.util.UUID;

/** A player's named home. {@code name} keeps the spelling the player typed. */
public record Home(UUID owner, String name, SavedLocation location, long createdAt) {
    public String key() {
        return Names.key(name);
    }
}
