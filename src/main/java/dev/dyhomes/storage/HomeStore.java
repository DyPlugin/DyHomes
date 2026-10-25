package dev.dyhomes.storage;

import dev.dyhomes.model.Home;
import dev.dyhomes.model.Names;
import dev.dyhomes.model.SavedLocation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Homes in homes/&lt;uuid&gt;.yml, one file per player, loaded on first use and kept until the player leaves.
 * A file that cannot be read is never written over: its owner's homes stay read-only until it is fixed.
 */
public class HomeStore {
    private final File folder;
    private final Logger logger;
    private final Map<UUID, Map<String, Home>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> unreadable = ConcurrentHashMap.newKeySet();

    public HomeStore(File dataFolder, Logger logger) {
        this.folder = new File(dataFolder, "homes");
        this.logger = logger;
    }

    public List<Home> homes(UUID owner) {
        Map<String, Home> homes = loaded(owner);
        synchronized (homes) {
            List<Home> list = new ArrayList<>(homes.values());
            list.sort(Comparator.comparing(Home::key));
            return list;
        }
    }

    public Optional<Home> home(UUID owner, String name) {
        Map<String, Home> homes = loaded(owner);
        synchronized (homes) {
            return Optional.ofNullable(homes.get(Names.key(name)));
        }
    }

    public int count(UUID owner) {
        Map<String, Home> homes = loaded(owner);
        synchronized (homes) {
            return homes.size();
        }
    }

    /** True when the player's file could not be read and their homes must not be changed. */
    public boolean locked(UUID owner) {
        loaded(owner);
        return unreadable.contains(owner);
    }

    /** Adds or moves a home and writes the file; the previous home is returned. Nothing changes if writing fails. */
    public Home put(Home home) throws IOException {
        Map<String, Home> homes = loaded(home.owner());
        synchronized (homes) {
            if (unreadable.contains(home.owner())) throw new IOException("homes file for " + home.owner() + " is unreadable");
            Home previous = homes.put(home.key(), home);
            try {
                write(home.owner(), homes);
            } catch (IOException failure) {
                if (previous == null) homes.remove(home.key());
                else homes.put(home.key(), previous);
                throw failure;
            }
            return previous;
        }
    }

    /** Removes a home and writes the file; null when there was no such home. */
    public Home remove(UUID owner, String name) throws IOException {
        Map<String, Home> homes = loaded(owner);
        synchronized (homes) {
            if (unreadable.contains(owner)) throw new IOException("homes file for " + owner + " is unreadable");
            Home removed = homes.remove(Names.key(name));
            if (removed == null) return null;
            try {
                write(owner, homes);
            } catch (IOException failure) {
                homes.put(removed.key(), removed);
                throw failure;
            }
            return removed;
        }
    }

    public void unload(UUID owner) {
        cache.remove(owner);
        unreadable.remove(owner);
    }

    public void clearCache() {
        cache.clear();
        unreadable.clear();
    }

    /** Players with a homes file, for admin listings and placeholders of offline players. */
    public List<UUID> owners() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        List<UUID> owners = new ArrayList<>();
        if (files != null) for (File file : files) {
            try {
                owners.add(UUID.fromString(file.getName().substring(0, file.getName().length() - 4)));
            } catch (IllegalArgumentException ignored) {
                // Not a homes file.
            }
        }
        return owners;
    }

    private Map<String, Home> loaded(UUID owner) {
        return cache.computeIfAbsent(owner, this::read);
    }

    private Map<String, Home> read(UUID owner) {
        Map<String, Home> homes = new LinkedHashMap<>();
        File file = file(owner);
        try {
            YamlConfiguration data = YamlFiles.load(file);
            ConfigurationSection section = data.getConfigurationSection("homes");
            if (section != null) for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                SavedLocation location = SavedLocation.read(entry);
                String name = entry == null ? null : entry.getString("name", key);
                if (location == null || !Names.valid(name)) {
                    logger.warning("Skipping invalid home '" + key + "' in " + file.getName());
                    continue;
                }
                homes.put(Names.key(name), new Home(owner, name, location, entry.getLong("created", 0L)));
            }
        } catch (Exception exception) {
            unreadable.add(owner);
            logger.severe("Could not read " + file.getPath() + "; these homes stay read-only until the file is fixed: "
                    + exception.getMessage());
        }
        return homes;
    }

    private void write(UUID owner, Map<String, Home> homes) throws IOException {
        File file = file(owner);
        if (homes.isEmpty()) {
            java.nio.file.Files.deleteIfExists(file.toPath());
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        for (Home home : homes.values()) {
            ConfigurationSection entry = data.createSection("homes." + home.key());
            entry.set("name", home.name());
            entry.set("created", home.createdAt());
            home.location().write(entry);
        }
        YamlFiles.save(data, file);
    }

    private File file(UUID owner) {
        return new File(folder, owner + ".yml");
    }
}
