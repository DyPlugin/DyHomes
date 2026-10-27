package dev.dyhomes.importer;

import dev.dyhomes.model.Home;
import dev.dyhomes.model.Names;
import dev.dyhomes.model.SavedLocation;
import dev.dyhomes.model.Warp;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reads homes (with their public flag) and warps from HuskHomes 4.x. Only its default SQLite database is supported;
 * it is copied first, so a running HuskHomes is never locked or changed. Table names follow HuskHomes' config.yml.
 */
public final class HuskHomesImporter {
    private static final Pattern TABLE = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private final File folder;
    private final File temporaryFolder;
    private final Logger logger;

    public HuskHomesImporter(File pluginsFolder, File temporaryFolder, Logger logger) {
        this.folder = new File(pluginsFolder, "HuskHomes");
        this.temporaryFolder = temporaryFolder;
        this.logger = logger;
    }

    public boolean present() {
        return new File(folder, "HuskHomesData.db").isFile() || new File(folder, "config.yml").isFile();
    }

    /** The database type from HuskHomes' config.yml, upper case; SQLITE when it is not set. */
    public String databaseType() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        return config.getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);
    }

    public Result read() throws Exception {
        Result result = new Result();
        File database = new File(folder, "HuskHomesData.db");
        if (!database.isFile()) throw new IllegalStateException("HuskHomesData.db not found");
        Map<String, String> tables = tables();
        temporaryFolder.mkdirs();
        File copy = new File(temporaryFolder, "huskhomes-import.db");
        Files.copy(database.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + copy.getAbsolutePath());
                 Statement statement = connection.createStatement()) {
                try (ResultSet users = statement.executeQuery("SELECT uuid, username FROM " + tables.get("PLAYER_DATA"))) {
                    while (users.next()) {
                        UUID id = uuid(users.getString("uuid"));
                        if (id != null) result.names.put(id, users.getString("username"));
                    }
                }
                String saved = "SELECT s.name, s.timestamp, p.x, p.y, p.z, p.yaw, p.pitch, p.world_name, p.world_uuid FROM "
                        + tables.get("SAVED_POSITION_DATA") + " s JOIN " + tables.get("POSITION_DATA") + " p ON p.id = s.position_id";
                try (ResultSet homes = statement.executeQuery(saved.replace("SELECT s.name", "SELECT h.owner_uuid, h.public, s.name")
                        + " JOIN " + tables.get("HOME_DATA") + " h ON h.saved_position_id = s.id")) {
                    while (homes.next()) {
                        UUID owner = uuid(homes.getString("owner_uuid"));
                        String name = homes.getString("name");
                        SavedLocation location = location(homes);
                        if (owner == null || location == null || !Names.valid(name)) {
                            result.skipped++;
                            continue;
                        }
                        result.homes.add(new Home(owner, name, location, timestamp(homes), homes.getBoolean("public")));
                    }
                }
                try (ResultSet warps = statement.executeQuery(saved + " JOIN " + tables.get("WARP_DATA") + " w ON w.saved_position_id = s.id")) {
                    while (warps.next()) {
                        String name = warps.getString("name");
                        SavedLocation location = location(warps);
                        if (location == null || !Names.valid(name)) {
                            result.skipped++;
                            continue;
                        }
                        result.warps.add(new Warp(name, location, null, timestamp(warps)));
                    }
                }
            }
        } finally {
            try {
                Files.deleteIfExists(copy.toPath());
            } catch (Exception ignored) {
                logger.fine("Could not delete the temporary HuskHomes copy");
            }
        }
        return result;
    }

    /** HuskHomes table names, with its defaults; names that are not plain identifiers are refused. */
    private Map<String, String> tables() {
        Map<String, String> tables = new HashMap<>(Map.of(
                "PLAYER_DATA", "huskhomes_users",
                "POSITION_DATA", "huskhomes_position_data",
                "SAVED_POSITION_DATA", "huskhomes_saved_positions",
                "HOME_DATA", "huskhomes_homes",
                "WARP_DATA", "huskhomes_warps"));
        ConfigurationSection configured = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"))
                .getConfigurationSection("database.table_names");
        if (configured != null) for (String key : tables.keySet()) {
            String value = configured.getString(key);
            if (value != null) tables.put(key, value);
        }
        for (Map.Entry<String, String> entry : tables.entrySet()) {
            if (!TABLE.matcher(entry.getValue()).matches()) throw new IllegalArgumentException("Unexpected HuskHomes table name for " + entry.getKey());
        }
        return tables;
    }

    private static SavedLocation location(ResultSet row) throws Exception {
        String world = row.getString("world_name");
        double x = row.getDouble("x"), y = row.getDouble("y"), z = row.getDouble("z");
        if (world == null || world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new SavedLocation(world, uuid(row.getString("world_uuid")), x, y, z, row.getFloat("yaw"), row.getFloat("pitch"));
    }

    /** HuskHomes stores the creation time as epoch milliseconds in SQLite. */
    private static long timestamp(ResultSet row) {
        try {
            long value = row.getLong("timestamp");
            return value > 0 ? value : System.currentTimeMillis();
        } catch (Exception unreadable) {
            return System.currentTimeMillis();
        }
    }

    private static UUID uuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static final class Result {
        public final List<Home> homes = new ArrayList<>();
        public final List<Warp> warps = new ArrayList<>();
        /** Player names from HuskHomes, used for the public home list. */
        public final Map<UUID, String> names = new HashMap<>();
        public int skipped;
    }
}
