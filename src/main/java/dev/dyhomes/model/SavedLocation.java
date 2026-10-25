package dev.dyhomes.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * A stored position. The world is found by its UID first and its name second, so a renamed world
 * keeps its homes and a recreated world with the same name still works.
 */
public record SavedLocation(String world, UUID worldId, double x, double y, double z, float yaw, float pitch) {

    public static SavedLocation of(Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalArgumentException("Location has no world");
        return new SavedLocation(world.getName(), world.getUID(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /** The live location, or null while its world is not loaded. */
    public Location toLocation() {
        World loaded = worldId == null ? null : Bukkit.getWorld(worldId);
        if (loaded == null) loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch);
    }

    public int blockX() { return (int) Math.floor(x); }
    public int blockY() { return (int) Math.floor(y); }
    public int blockZ() { return (int) Math.floor(z); }

    public void write(ConfigurationSection section) {
        section.set("world", world);
        section.set("world-id", worldId == null ? null : worldId.toString());
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", (double) yaw);
        section.set("pitch", (double) pitch);
    }

    /** Reads a stored position; null when the entry is incomplete. */
    public static SavedLocation read(ConfigurationSection section) {
        if (section == null) return null;
        String world = section.getString("world");
        if (world == null || world.isBlank() || !section.isSet("x") || !section.isSet("y") || !section.isSet("z")) return null;
        double x = section.getDouble("x"), y = section.getDouble("y"), z = section.getDouble("z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new SavedLocation(world, parseId(section.getString("world-id")), x, y, z,
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    static UUID parseId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
