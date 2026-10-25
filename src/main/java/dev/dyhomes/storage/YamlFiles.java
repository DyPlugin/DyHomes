package dev.dyhomes.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic YAML writes: a crash or full disk leaves either the old file or the new one, never half of it. */
public final class YamlFiles {
    private YamlFiles() {
    }

    public static void save(YamlConfiguration data, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists())
            throw new IOException("Could not create " + parent);
        Path temporary = file.toPath().resolveSibling(file.getName() + ".tmp");
        Files.writeString(temporary, data.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Loads a file; a missing file is empty, an unreadable one throws so it is never overwritten. */
    public static YamlConfiguration load(File file) throws Exception {
        YamlConfiguration data = new YamlConfiguration();
        if (file.exists()) data.load(file);
        return data;
    }
}
