package dev.dyhomes.storage;

import dev.dyhomes.model.SavedLocation;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/** The server spawn set with /setspawn, in spawn.yml. */
public class SpawnStore {
    private final File file;
    private final Logger logger;
    private volatile SavedLocation spawn;
    private volatile boolean locked;

    public SpawnStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "spawn.yml");
        this.logger = logger;
        reload();
    }

    public synchronized void reload() {
        try {
            spawn = SavedLocation.read(YamlFiles.load(file).getConfigurationSection("spawn"));
            locked = false;
        } catch (Exception exception) {
            locked = true;
            logger.severe("Could not read spawn.yml; the spawn stays as it was until the file is fixed: " + exception.getMessage());
        }
    }

    /** The spawn set with /setspawn, or null. */
    public SavedLocation spawn() {
        return spawn;
    }

    public synchronized void set(SavedLocation location) throws IOException {
        if (locked) throw new IOException("spawn.yml is unreadable");
        YamlConfiguration data = new YamlConfiguration();
        location.write(data.createSection("spawn"));
        YamlFiles.save(data, file);
        spawn = location;
    }
}
