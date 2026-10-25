package dev.dyhomes;

import dev.dyhomes.lang.Language;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Message files, plugin.yml and the default config agree with the code. */
class ResourcesTest {
    private static YamlConfiguration resource(String name) {
        var stream = ResourcesTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, name);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test void everyLanguageHasExactlyTheEnglishKeys() {
        Set<String> english = new TreeSet<>(resource("messages_en.yml").getKeys(true));
        for (Language language : Language.values()) {
            Set<String> keys = new TreeSet<>(resource(language.fileName()).getKeys(true));
            assertEquals(english, keys, language.fileName());
        }
    }

    @Test void everyMessageTheCodeSendsExists() throws Exception {
        YamlConfiguration english = resource("messages_en.yml");
        YamlConfiguration config = resource("config.yml");
        Pattern used = Pattern.compile("\"((?:general|homes|warps|spawn|teleport|import|list|help|time|words|commands|value|updates)\\.[a-z.-]+)\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = used.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (key.endsWith("-") || key.endsWith(".")) continue; // Built from a prefix, checked below.
                    if (key.endsWith(".yml") || config.contains(key)) continue; // A file name or a setting.
                    if (!english.contains(key)) missing.add(key + " in " + file.getFileName());
                }
            }
        }
        for (String suffix : Set.of("home", "warp", "spawn")) assertTrue(english.contains("teleport.success-" + suffix));
        for (String reason : Set.of("suffocation", "hazard", "fall", "void")) assertTrue(english.contains("teleport.unsafe-" + reason));
        for (String reason : Set.of("move", "damage")) assertTrue(english.contains("teleport.cancel-" + reason));
        for (String code : Set.of("auto", "en", "tr", "de", "es", "ru")) assertTrue(english.contains("value.language-" + code));
        assertTrue(missing.isEmpty(), "missing messages: " + missing);
    }

    @Test void theDefaultConfigIsValidAndBadValuesAreRejected() {
        YamlConfiguration config = resource("config.yml");
        ConfigValidator.validate(config);
        config.set("homes.default-limit", -1);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("homes.default-limit", 3);
        config.set("economy.prices.home", -5);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("economy.prices.home", 0);
        config.set("dyclaim.use-right", "fly");
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("dyclaim.use-right", "teleport");
        config.set("spawn.on-respawn", "yes");
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
        config.set("spawn.on-respawn", true);
        config.set("language.default", "fr");
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test void pluginYmlDeclaresEveryCommandAndFolia() {
        YamlConfiguration plugin = resource("plugin.yml");
        assertTrue(plugin.getBoolean("folia-supported"));
        for (String command : Set.of("home", "homes", "sethome", "delhome", "warp", "warps", "setwarp", "delwarp",
                "spawn", "setspawn", "dyhomes")) {
            assertTrue(plugin.isConfigurationSection("commands." + command), command);
        }
        assertTrue(resource("messages_tr.yml").getString("commands.home").equals("/ev")
                && plugin.getStringList("commands.home.aliases").contains("ev"), "the Turkish hint command exists");
    }
}
