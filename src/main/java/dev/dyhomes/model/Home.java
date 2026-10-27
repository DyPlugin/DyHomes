package dev.dyhomes.model;

import java.util.UUID;

/**
 * A player's named home. {@code name} keeps the spelling the player typed. A public home can be visited by
 * other players with /phome.
 */
public record Home(UUID owner, String name, SavedLocation location, long createdAt, boolean publicHome) {
    public Home(UUID owner, String name, SavedLocation location, long createdAt) {
        this(owner, name, location, createdAt, false);
    }

    public String key() {
        return Names.key(name);
    }

    public Home withLocation(SavedLocation moved) {
        return new Home(owner, name, moved, createdAt, publicHome);
    }

    public Home withPublic(boolean value) {
        return new Home(owner, name, location, createdAt, value);
    }
}
