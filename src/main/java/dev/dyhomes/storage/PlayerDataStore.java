package dev.dyhomes.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/** Saved language choices in players.yml. */
public class PlayerDataStore {
    private final File file;
    private final Logger logger;
    private YamlConfiguration data;

    public PlayerDataStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "players.yml");
        this.logger = logger;
        reload();
    }

    public synchronized void reload() {
        try {
            data = YamlFiles.load(file);
        } catch (Exception exception) {
            if (data == null) throw new IllegalStateException("Could not load players.yml; refusing to overwrite data", exception);
            logger.warning("Could not reload players.yml; existing preferences retained: " + exception.getMessage());
        }
    }

    public synchronized String getLanguage(UUID uuid) {
        return data.getString("players." + uuid + ".language");
    }

    public synchronized void setLanguage(UUID uuid, String language) {
        if (language == null || language.equalsIgnoreCase("auto")) data.set("players." + uuid + ".language", null);
        else data.set("players." + uuid + ".language", language.toLowerCase(Locale.ROOT));
        try {
            YamlFiles.save(data, file);
        } catch (IOException exception) {
            logger.warning("Could not save players.yml: " + exception.getMessage());
        }
    }
}
