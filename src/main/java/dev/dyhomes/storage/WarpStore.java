package dev.dyhomes.storage;

import dev.dyhomes.model.Names;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.model.Warp;
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
import java.util.UUID;
import java.util.logging.Logger;

/** Warps in warps.yml. Like homes, an unreadable file keeps the running warps and is never written over. */
public class WarpStore {
    private final File file;
    private final Logger logger;
    private Map<String, Warp> warps = new LinkedHashMap<>();
    private boolean locked;

    public WarpStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "warps.yml");
        this.logger = logger;
        reload();
    }

    public synchronized void reload() {
        Map<String, Warp> loaded = new LinkedHashMap<>();
        try {
            YamlConfiguration data = YamlFiles.load(file);
            ConfigurationSection section = data.getConfigurationSection("warps");
            if (section != null) for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                SavedLocation location = SavedLocation.read(entry);
                String name = entry == null ? null : entry.getString("name", key);
                if (location == null || !Names.valid(name)) {
                    logger.warning("Skipping invalid warp '" + key + "' in warps.yml");
                    continue;
                }
                UUID creator = null;
                try {
                    String value = entry.getString("creator");
                    if (value != null) creator = UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // Unknown creator; the warp still works.
                }
                loaded.put(Names.key(name), new Warp(name, location, creator, entry.getLong("created", 0L)));
            }
            warps = loaded;
            locked = false;
        } catch (Exception exception) {
            locked = true;
            logger.severe("Could not read warps.yml; warps stay read-only until the file is fixed: " + exception.getMessage());
        }
    }

    public synchronized List<Warp> warps() {
        List<Warp> list = new ArrayList<>(warps.values());
        list.sort(Comparator.comparing(Warp::key));
        return list;
    }

    public synchronized Optional<Warp> warp(String name) {
        return Optional.ofNullable(warps.get(Names.key(name)));
    }

    public synchronized boolean locked() {
        return locked;
    }

    public synchronized Warp put(Warp warp) throws IOException {
        if (locked) throw new IOException("warps.yml is unreadable");
        Warp previous = warps.put(warp.key(), warp);
        try {
            write();
        } catch (IOException failure) {
            if (previous == null) warps.remove(warp.key());
            else warps.put(warp.key(), previous);
            throw failure;
        }
        return previous;
    }

    public synchronized Warp remove(String name) throws IOException {
        if (locked) throw new IOException("warps.yml is unreadable");
        Warp removed = warps.remove(Names.key(name));
        if (removed == null) return null;
        try {
            write();
        } catch (IOException failure) {
            warps.put(removed.key(), removed);
            throw failure;
        }
        return removed;
    }

    private void write() throws IOException {
        YamlConfiguration data = new YamlConfiguration();
        data.createSection("warps");
        for (Warp warp : warps.values()) {
            ConfigurationSection entry = data.createSection("warps." + warp.key());
            entry.set("name", warp.name());
            entry.set("creator", warp.creator() == null ? null : warp.creator().toString());
            entry.set("created", warp.createdAt());
            warp.location().write(entry);
        }
        YamlFiles.save(data, file);
    }
}
