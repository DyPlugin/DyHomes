package dev.dyhomes.storage;

import dev.dyhomes.model.Home;
import dev.dyhomes.model.Names;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Which homes are public, in public-homes.yml, so /phomes can list them without reading every player's file.
 * The homes files stay the source of truth: a home is only visited after its owner's file confirms it is public.
 */
public class PublicHomeIndex {
    /** One public home: its owner, the owner's name when it was shared, and the home name. */
    public record Entry(UUID owner, String ownerName, String home) {
        public String key() {
            return owner + ":" + Names.key(home);
        }
    }

    private final File file;
    private final Logger logger;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded;

    public PublicHomeIndex(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "public-homes.yml");
        this.logger = logger;
        reload();
    }

    public synchronized void reload() {
        entries.clear();
        loaded = file.exists();
        try {
            ConfigurationSection section = YamlFiles.load(file).getConfigurationSection("homes");
            if (section != null) for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;
                try {
                    Entry value = new Entry(UUID.fromString(entry.getString("owner", "")), entry.getString("owner-name", "?"),
                            entry.getString("home", ""));
                    if (Names.valid(value.home())) entries.put(value.key(), value);
                } catch (IllegalArgumentException invalid) {
                    logger.warning("Skipping invalid public home '" + key + "' in public-homes.yml");
                }
            }
        } catch (Exception exception) {
            logger.warning("Could not read public-homes.yml; it will be rebuilt: " + exception.getMessage());
            loaded = false;
        }
    }

    /** False before the first save, for example right after upgrading from 1.0.0-BETA. */
    public synchronized boolean loaded() {
        return loaded;
    }

    public synchronized List<Entry> entries() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing((Entry entry) -> entry.ownerName().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(entry -> Names.key(entry.home())));
        return list;
    }

    /** Records whether a home is public; non-public or deleted homes are removed. */
    public synchronized void update(Home home, String ownerName, boolean exists) {
        String key = home.owner() + ":" + home.key();
        boolean changed;
        if (exists && home.publicHome()) {
            Entry previous = entries.put(key, new Entry(home.owner(), ownerName, home.name()));
            changed = previous == null || !previous.ownerName().equals(ownerName) || !previous.home().equals(home.name());
        } else {
            changed = entries.remove(key) != null;
        }
        if (changed) save();
    }

    /** Replaces the whole index, used when it is rebuilt from the homes files. */
    public synchronized void replace(List<Entry> rebuilt) {
        entries.clear();
        for (Entry entry : rebuilt) entries.put(entry.key(), entry);
        save();
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.createSection("homes");
        int i = 0;
        for (Entry entry : entries.values()) {
            ConfigurationSection section = data.createSection("homes." + i++);
            section.set("owner", entry.owner().toString());
            section.set("owner-name", entry.ownerName());
            section.set("home", entry.home());
        }
        try {
            YamlFiles.save(data, file);
            loaded = true;
        } catch (IOException exception) {
            logger.warning("Could not save public-homes.yml: " + exception.getMessage());
        }
    }
}
