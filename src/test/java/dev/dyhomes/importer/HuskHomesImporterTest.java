package dev.dyhomes.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Uses a real HuskHomes 4.11 SQLite database made on a test server: three homes (one public) and two warps. */
class HuskHomesImporterTest {
    @TempDir File plugins;

    private void fixture(String name) throws Exception {
        File target = new File(plugins, "HuskHomes/" + name);
        target.getParentFile().mkdirs();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("huskhomes/" + name)) {
            assertNotNull(in, name);
            Files.copy(in, target.toPath());
        }
    }

    @Test void readsHomesPublicFlagWarpsAndNames() throws Exception {
        fixture("HuskHomesData.db");
        fixture("config.yml");
        HuskHomesImporter importer = new HuskHomesImporter(plugins, new File(plugins, "tmp"), Logger.getLogger("test"));
        assertTrue(importer.present());
        assertEquals("SQLITE", importer.databaseType());
        HuskHomesImporter.Result result = importer.read();
        UUID owner = UUID.fromString("bf75cbbf-10d7-3ed2-b862-e41dd6615300");
        assertEquals("HuskSeed", result.names.get(owner));
        assertEquals(3, result.homes.size());
        var shop = result.homes.stream().filter(home -> home.name().equals("shop")).findFirst().orElseThrow();
        assertTrue(shop.publicHome());
        assertEquals(owner, shop.owner());
        assertEquals(55.5, shop.location().x());
        assertEquals(-5.5, shop.location().z());
        assertEquals("world", shop.location().world());
        assertNotNull(shop.location().worldId());
        assertFalse(result.homes.stream().filter(home -> home.name().equals("base")).findFirst().orElseThrow().publicHome());
        assertEquals(java.util.Set.of("market", "arena"), result.warps.stream().map(warp -> warp.name()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(0, result.skipped);
        assertFalse(new File(plugins, "tmp/huskhomes-import.db").exists(), "the temporary copy is removed");
        assertTrue(new File(plugins, "HuskHomes/HuskHomesData.db").exists(), "the HuskHomes database is untouched");
    }

    @Test void otherDatabasesAreRecognised() throws Exception {
        File config = new File(plugins, "HuskHomes/config.yml");
        config.getParentFile().mkdirs();
        Files.writeString(config.toPath(), "database:\n  type: MYSQL\n");
        assertEquals("MYSQL", new HuskHomesImporter(plugins, new File(plugins, "tmp"), Logger.getLogger("test")).databaseType());
    }
}
