package dev.dyhomes.model;

import java.util.UUID;

/** A server warp. {@code creator} is null for warps created from the console or imported. */
public record Warp(String name, SavedLocation location, UUID creator, long createdAt) {
    public String key() {
        return Names.key(name);
    }
}
