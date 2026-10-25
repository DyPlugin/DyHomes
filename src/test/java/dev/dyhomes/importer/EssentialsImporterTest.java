package dev.dyhomes.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EssentialsImporterTest {
    @TempDir File plugins;

    private void write(String path, String text) throws Exception {
        File file = new File(plugins, "Essentials/" + path);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), text);
    }

    @Test void readsCurrentAndOldFormatsAndSkipsBrokenEntries() throws Exception {
        UUID player = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        write("userdata/" + player + ".yml", """
                homes:
                  base:
                    world: %s
                    world-name: survival
                    x: 10.5
                    y: 64.0
                    z: -20.5
                    yaw: 90.0
                    pitch: 0.0
                  old:
                    world: world_nether
                    x: 1.0
                    y: 70.0
                    z: 1.0
                  broken:
                    world: survival
                """.formatted(worldId));
        write("userdata/not-a-uuid.yml", "homes: {}\n");
        write("warps/shop.yml", """
                name: Shop
                world: %s
                world-name: survival
                x: 0.5
                y: 65.0
                z: 0.5
                """.formatted(worldId));
        write("warps/bad.yml", "name: [unclosed\n");
        write("spawn.yml", """
                spawns:
                  default:
                    world: world
                    x: 0.5
                    y: 80.0
                    z: 0.5
                """);
        EssentialsImporter importer = new EssentialsImporter(plugins, Logger.getLogger("test"));
        assertTrue(importer.present());
        EssentialsImporter.Result result = importer.read();
        assertEquals(2, result.homes.size());
        var base = result.homes.stream().filter(home -> home.name().equals("base")).findFirst().orElseThrow();
        assertEquals(player, base.owner());
        assertEquals("survival", base.location().world());
        assertEquals(worldId, base.location().worldId());
        assertEquals(-20.5, base.location().z());
        var old = result.homes.stream().filter(home -> home.name().equals("old")).findFirst().orElseThrow();
        assertEquals("world_nether", old.location().world());
        assertNull(old.location().worldId());
        assertEquals(1, result.skipped);
        assertEquals(1, result.warps.size());
        assertEquals("Shop", result.warps.get(0).name());
        assertEquals(1, result.unreadable);
        assertEquals(80.0, result.spawn.y());
    }

    @Test void noEssentialsFolderIsReported() {
        assertFalse(new EssentialsImporter(plugins, Logger.getLogger("test")).present());
    }
}
