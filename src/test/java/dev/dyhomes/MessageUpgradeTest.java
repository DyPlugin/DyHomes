package dev.dyhomes;

import dev.dyhomes.lang.Language;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Upgrading from 1.0.0-BETA: untouched stock texts follow the new version, edited ones are kept. */
class MessageUpgradeTest {
    private static YamlConfiguration resource(String name) {
        var stream = MessageUpgradeTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, name);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test void stockHelpIsUpdatedAndEditedTextsAreKept() {
        for (Language language : Language.values()) {
            YamlConfiguration current = resource(language.fileName());
            YamlConfiguration beta = resource("legacy/" + language.fileName().replace(".yml", "-1.0.0-BETA.yml"));
            YamlConfiguration installed = resource("legacy/" + language.fileName().replace(".yml", "-1.0.0-BETA.yml"));
            installed.set("homes.set", "custom text");
            assertTrue(DyHomes.copyMissing(current, installed, beta));
            assertEquals(current.getStringList("help.player"), installed.getStringList("help.player"), language + " help list");
            assertEquals(current.getString("import.usage"), installed.getString("import.usage"));
            assertEquals(current.getString("back.none"), installed.getString("back.none"), "new keys are added");
            assertEquals("custom text", installed.getString("homes.set"), "edited texts stay");
            assertEquals(current.getKeys(true), installed.getKeys(true));
        }
    }
}
