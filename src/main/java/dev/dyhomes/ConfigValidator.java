package dev.dyhomes;

import dev.dyhomes.lang.Language;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

/** Validates a staged configuration before it replaces the running settings. */
final class ConfigValidator {
    private ConfigValidator() {
    }

    static void validate(FileConfiguration config) {
        String language = config.getString("language.default", "auto");
        if (!"auto".equals(language) && !Language.codes().contains(language))
            invalid("language.default", "expected auto or one of " + Language.codes());
        bounded(config, "homes.default-limit", 0, 100000);
        bounded(config, "warmup.seconds", 0, 3600);
        bounded(config, "safety.max-fall", 0, 64);
        for (String kind : Set.of("home", "warp", "spawn")) bounded(config, "cooldown." + kind, 0, Integer.MAX_VALUE);
        for (String action : Set.of("home", "warp", "spawn", "sethome")) price(config, "economy.prices." + action);
        stringList(config, "homes.disabled-worlds");
        for (String path : Set.of("dyclaim.set-right", "dyclaim.use-right")) {
            if (config.contains(path) && !Set.of("build", "containers", "doors", "trapdoors", "redstone", "entities", "teleport")
                    .contains(config.getString(path, ""))) invalid(path, "expected a DyClaim right such as build or teleport");
        }
        for (String key : config.getKeys(true)) {
            if ((key.endsWith(".enabled") || key.endsWith("-permission") || key.startsWith("spawn.")
                    || key.endsWith(".check") || key.endsWith("-overrides"))
                    && !config.isConfigurationSection(key) && !(config.get(key) instanceof Boolean)) {
                invalid(key, "expected true or false");
            }
        }
    }

    private static void price(FileConfiguration config, String path) {
        if (!config.contains(path)) return;
        Object value = config.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0)
            invalid(path, "expected a non-negative number");
    }

    private static void bounded(FileConfiguration config, String path, long min, long max) {
        if (!config.contains(path)) return;
        Object value = config.get(path);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.longValue() || number.longValue() < min || number.longValue() > max)
            invalid(path, "expected a whole number between " + min + " and " + max);
    }

    private static void stringList(FileConfiguration config, String path) {
        if (!config.contains(path)) return;
        if (!(config.get(path) instanceof java.util.List<?> values) || values.stream().anyMatch(item -> !(item instanceof String)))
            invalid(path, "expected a list of names");
    }

    private static void invalid(String path, String reason) {
        throw new IllegalArgumentException(path + ": " + reason);
    }
}
