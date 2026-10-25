package dev.dyhomes.importer;

import dev.dyhomes.model.Home;
import dev.dyhomes.model.Names;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.model.Warp;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads homes, warps and spawn from EssentialsX files: userdata/&lt;uuid&gt;.yml, warps/&lt;name&gt;.yml and
 * spawn.yml from EssentialsSpawn. Only reads; the caller decides what to keep. Both the current format
 * (world as UID plus world-name) and the older one (world as name) are understood.
 */
public final class EssentialsImporter {
    private final File essentials;
    private final Logger logger;

    public EssentialsImporter(File pluginsFolder, Logger logger) {
        this.essentials = new File(pluginsFolder, "Essentials");
        this.logger = logger;
    }

    public boolean present() {
        return essentials.isDirectory();
    }

    public Result read() {
        Result result = new Result();
        File[] users = new File(essentials, "userdata").listFiles((dir, name) -> name.endsWith(".yml"));
        if (users != null) for (File file : users) {
            UUID owner;
            try {
                owner = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            } catch (IllegalArgumentException notUuid) {
                continue;
            }
            YamlConfiguration data = load(file, result);
            ConfigurationSection homes = data == null ? null : data.getConfigurationSection("homes");
            if (homes == null) continue;
            for (String name : homes.getKeys(false)) {
                SavedLocation location = location(homes.getConfigurationSection(name));
                if (location == null || !Names.valid(name)) {
                    result.skipped++;
                    continue;
                }
                result.homes.add(new Home(owner, name, location, System.currentTimeMillis()));
            }
        }
        File[] warps = new File(essentials, "warps").listFiles((dir, name) -> name.endsWith(".yml"));
        if (warps != null) for (File file : warps) {
            YamlConfiguration data = load(file, result);
            if (data == null) continue;
            String name = data.getString("name", file.getName().substring(0, file.getName().length() - 4));
            SavedLocation location = location(data);
            if (location == null || !Names.valid(name)) {
                result.skipped++;
                continue;
            }
            result.warps.add(new Warp(name, location, null, System.currentTimeMillis()));
        }
        File spawnFile = new File(essentials, "spawn.yml");
        if (spawnFile.isFile()) {
            YamlConfiguration data = load(spawnFile, result);
            if (data != null) result.spawn = location(data.getConfigurationSection("spawns.default"));
        }
        return result;
    }

    private YamlConfiguration load(File file, Result result) {
        try {
            YamlConfiguration data = new YamlConfiguration();
            data.load(file);
            return data;
        } catch (Exception exception) {
            result.unreadable++;
            logger.warning("Skipping unreadable Essentials file " + file.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    /** One Essentials location; "world" is a UID in current versions and a name in old ones. */
    static SavedLocation location(ConfigurationSection section) {
        if (section == null || !section.isSet("x") || !section.isSet("y") || !section.isSet("z")) return null;
        String world = section.getString("world");
        String worldName = section.getString("world-name");
        UUID worldId = null;
        if (world != null) {
            try {
                worldId = UUID.fromString(world);
            } catch (IllegalArgumentException isName) {
                if (worldName == null) worldName = world;
            }
        }
        if (worldName == null && worldId != null) {
            org.bukkit.World loaded = org.bukkit.Bukkit.getWorld(worldId);
            if (loaded != null) worldName = loaded.getName();
        }
        if (worldName == null || worldName.isBlank()) return null;
        double x = section.getDouble("x"), y = section.getDouble("y"), z = section.getDouble("z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new SavedLocation(worldName, worldId, x, y, z, (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public static final class Result {
        public final List<Home> homes = new ArrayList<>();
        public final List<Warp> warps = new ArrayList<>();
        public SavedLocation spawn;
        public int skipped;
        public int unreadable;
    }
}
