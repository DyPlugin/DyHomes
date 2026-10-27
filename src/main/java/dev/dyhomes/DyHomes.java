package dev.dyhomes;

import dev.dyhomes.claim.ClaimAccess;
import dev.dyhomes.command.BackCommand;
import dev.dyhomes.command.DyHomesCommand;
import dev.dyhomes.command.HomeCommands;
import dev.dyhomes.command.SpawnCommands;
import dev.dyhomes.command.WarpCommands;
import dev.dyhomes.economy.EconomyService;
import dev.dyhomes.lang.Language;
import dev.dyhomes.lang.MessageService;
import dev.dyhomes.listener.PlayerListener;
import dev.dyhomes.map.MapService;
import dev.dyhomes.rank.RankRules;
import dev.dyhomes.scheduler.Scheduler;
import dev.dyhomes.storage.HomeStore;
import dev.dyhomes.storage.PlayerDataStore;
import dev.dyhomes.storage.PublicHomeIndex;
import dev.dyhomes.storage.SpawnStore;
import dev.dyhomes.storage.WarpStore;
import dev.dyhomes.teleport.BackService;
import dev.dyhomes.teleport.TeleportService;
import dev.dyhomes.update.ModrinthUpdateChecker;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class DyHomes extends JavaPlugin {
    /** bStats plugin id for DyHomes (https://bstats.org/plugin/bukkit/DyHomes/34939). */
    static final int BSTATS_ID = 34939;

    private static DyHomes instance;

    private Scheduler scheduler;
    private PlayerDataStore playerData;
    private HomeStore homes;
    private WarpStore warps;
    private SpawnStore spawns;
    private PublicHomeIndex publicHomes;
    private BackService back;
    private MapService maps;
    private MessageService messages;
    private RankRules ranks;
    private EconomyService economy;
    private ClaimAccess claims;
    private TeleportService teleports;
    private SpawnCommands spawnCommands;
    private ModrinthUpdateChecker updateChecker;
    private Object placeholders;
    private dev.dyhomes.lib.bstats.bukkit.Metrics metrics;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!validateFiles()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getConfig().options().copyDefaults(true);
        saveConfig();
        for (Language language : Language.values()) ensureMessageDefaults(language.fileName());

        scheduler = new Scheduler(this);
        playerData = new PlayerDataStore(getDataFolder(), getLogger());
        homes = new HomeStore(getDataFolder(), getLogger());
        warps = new WarpStore(getDataFolder(), getLogger());
        spawns = new SpawnStore(getDataFolder(), getLogger());
        publicHomes = new PublicHomeIndex(getDataFolder(), getLogger());
        back = new BackService(this::getConfig);
        messages = new MessageService(this, playerData);
        ranks = new RankRules(this::getConfig);
        economy = new EconomyService(this);
        claims = new ClaimAccess(this);
        teleports = new TeleportService(this, messages, economy, claims, scheduler, ranks);
        spawnCommands = new SpawnCommands(this, messages, spawns, teleports);

        HomeCommands homeCommands = new HomeCommands(this, messages, homes, teleports, claims, economy, ranks, publicHomes);
        WarpCommands warpCommands = new WarpCommands(this, messages, warps, teleports);
        register(homeCommands, "home", "homes", "sethome", "delhome", "publichome", "phome", "phomes");
        register(new BackCommand(this, messages, back, teleports), "back");
        register(warpCommands, "warp", "warps", "setwarp", "delwarp");
        register(spawnCommands, "spawn", "setspawn");
        register(new DyHomesCommand(this, messages, playerData, homes, warps, spawns, publicHomes), "dyhomes");

        getServer().getPluginManager().registerEvents(new PlayerListener(this, teleports, homes, spawnCommands, back), this);
        startPlaceholders();
        startMetrics();
        maps = new MapService(this);
        // Map plugins may enable after DyHomes; start the markers once the server has finished loading.
        scheduler.runGlobal(maps::start);
        if (!publicHomes.loaded()) rebuildPublicHomes();
        updateChecker = new ModrinthUpdateChecker(this, messages);
        updateChecker.start();
        getLogger().info("DyHomes " + getDescription().getVersion() + " enabled" + (Scheduler.FOLIA ? " (Folia)." : "."));
    }

    @Override
    public void onDisable() {
        if (teleports != null) teleports.cancelAll();
        if (maps != null) maps.stop();
        if (updateChecker != null) updateChecker.cancel();
        stopPlaceholders();
        if (metrics != null) metrics.shutdown();
        if (scheduler != null) scheduler.cancelAll();
        getLogger().info("DyHomes disabled.");
    }

    private <T extends CommandExecutor & TabCompleter> void register(T handler, String... names) {
        for (String name : names) {
            PluginCommand command = getCommand(name);
            if (command == null) continue;
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    public boolean tryReloadPlugin() {
        if (!validateFiles()) return false;
        teleports.cancelAll();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        for (Language language : Language.values()) ensureMessageDefaults(language.fileName());
        playerData.reload();
        homes.clearCache();
        warps.reload();
        spawns.reload();
        publicHomes.reload();
        messages.reload();
        economy.reload();
        updateChecker.reload();
        stopPlaceholders();
        startPlaceholders();
        maps.start();
        return true;
    }

    /** Applies setting changes only if the whole staged configuration is valid, then reloads. */
    public boolean applyConfigChanges(Map<String, Object> changes) {
        try {
            YamlConfiguration staged = new YamlConfiguration();
            staged.loadFromString(getConfig().saveToString());
            changes.forEach(staged::set);
            ConfigValidator.validate(staged);
            dev.dyhomes.storage.YamlFiles.save(staged, new File(getDataFolder(), "config.yml"));
            return tryReloadPlugin();
        } catch (Exception exception) {
            getLogger().warning("Setting change rejected; running settings retained: " + exception.getMessage());
            return false;
        }
    }

    private boolean validateFiles() {
        try {
            YamlConfiguration staged = new YamlConfiguration();
            staged.load(new File(getDataFolder(), "config.yml"));
            ConfigValidator.validate(staged);
            for (Language language : Language.values()) {
                File file = new File(getDataFolder(), language.fileName());
                if (file.exists()) new YamlConfiguration().load(file);
            }
            return true;
        } catch (Exception exception) {
            getLogger().warning("Configuration rejected; running settings retained: " + exception.getMessage());
            return false;
        }
    }

    public static DyHomes getInstance() { return instance; }
    public Scheduler scheduler() { return scheduler; }
    public HomeStore homeStore() { return homes; }
    public WarpStore warpStore() { return warps; }
    public SpawnStore spawnStore() { return spawns; }
    public MessageService messages() { return messages; }
    public RankRules ranks() { return ranks; }
    public TeleportService teleports() { return teleports; }
    public PublicHomeIndex publicHomes() { return publicHomes; }
    public BackService back() { return back; }
    public MapService maps() { return maps; }
    public Location spawnLocation() { return spawnCommands == null ? null : spawnCommands.spawnLocation(); }

    /** PlaceholderAPI classes are only touched when that plugin is present and the switch is on. */
    private void startPlaceholders() {
        if (placeholders != null || !getConfig().getBoolean("integrations.placeholderapi.enabled", true)) return;
        org.bukkit.plugin.Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) return;
        try {
            placeholders = dev.dyhomes.hook.PlaceholderHook.register(this);
            if (placeholders != null) getLogger().info("PlaceholderAPI placeholders registered (%dyhomes_...%).");
        } catch (LinkageError | RuntimeException exception) {
            getLogger().warning("PlaceholderAPI hook failed: " + exception);
        }
    }

    private void stopPlaceholders() {
        if (placeholders == null) return;
        try {
            dev.dyhomes.hook.PlaceholderHook.unregister(placeholders);
        } catch (LinkageError | RuntimeException ignored) {
            // PlaceholderAPI is already gone.
        }
        placeholders = null;
    }

    /** Builds public-homes.yml from the homes files, for servers upgrading from a version without it. */
    private void rebuildPublicHomes() {
        scheduler.runAsync(() -> {
            java.util.List<PublicHomeIndex.Entry> entries = new java.util.ArrayList<>();
            for (java.util.UUID owner : homes.owners()) {
                for (dev.dyhomes.model.Home home : homes.peek(owner)) {
                    if (!home.publicHome()) continue;
                    String name = getServer().getOfflinePlayer(owner).getName();
                    entries.add(new PublicHomeIndex.Entry(owner, name == null ? owner.toString().substring(0, 8) : name, home.name()));
                }
            }
            publicHomes.replace(entries);
        });
    }

    private void startMetrics() {
        if (BSTATS_ID <= 0 || !getConfig().getBoolean("metrics.enabled", true)) return;
        try {
            metrics = new dev.dyhomes.lib.bstats.bukkit.Metrics(this, BSTATS_ID);
            metrics.addCustomChart(new dev.dyhomes.lib.bstats.charts.SimplePie("language_mode", () -> getConfig().getString("language.default", "auto")));
            metrics.addCustomChart(new dev.dyhomes.lib.bstats.charts.SimplePie("economy", () -> economy.enabled() && economy.available() ? "On" : "Off"));
            metrics.addCustomChart(new dev.dyhomes.lib.bstats.charts.SimplePie("default_home_limit", () -> String.valueOf(getConfig().getInt("homes.default-limit", 3))));
            metrics.addCustomChart(new dev.dyhomes.lib.bstats.charts.SimplePie("placeholderapi", () -> placeholders != null ? "Yes" : "No"));
            metrics.addCustomChart(new dev.dyhomes.lib.bstats.charts.SimplePie("dyclaim", () -> getServer().getPluginManager().isPluginEnabled("DyClaim") ? "Yes" : "No"));
            metrics.addCustomChart(new dev.dyhomes.lib.bstats.charts.SimplePie("folia", () -> Scheduler.FOLIA ? "Yes" : "No"));
        } catch (RuntimeException exception) {
            getLogger().warning("bStats could not start: " + exception.getMessage());
        }
    }

    private void ensureMessageDefaults(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            return;
        }
        try (InputStream input = getResource(fileName)) {
            if (input == null) return;
            YamlConfiguration existing = new YamlConfiguration();
            existing.load(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            YamlConfiguration stock = legacy(fileName);
            if (copyMissing(defaults, existing, stock)) existing.save(file);
        } catch (Exception exception) {
            getLogger().warning("Could not add missing message keys to " + fileName + ": " + exception.getMessage());
        }
    }

    /** The bundled 1.0.0-BETA text of a message file, used to recognise texts nobody edited. */
    private YamlConfiguration legacy(String fileName) {
        try (InputStream input = getResource("legacy/" + fileName.replace(".yml", "-1.0.0-BETA.yml"))) {
            if (input == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return new YamlConfiguration();
        }
    }

    /**
     * Adds keys that are new in this version. A text is only replaced when it is still exactly the stock text of
     * 1.0.0-BETA (for example a help list that now has more commands); edited texts are never changed.
     */
    static boolean copyMissing(ConfigurationSection defaults, ConfigurationSection target, ConfigurationSection stock) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            ConfigurationSection section = defaults.getConfigurationSection(key);
            if (section != null) {
                ConfigurationSection existing = target.getConfigurationSection(key);
                if (existing == null) {
                    if (target.contains(key)) continue;
                    existing = target.createSection(key);
                    changed = true;
                }
                ConfigurationSection oldStock = stock == null ? null : stock.getConfigurationSection(key);
                changed |= copyMissing(section, existing, oldStock);
            } else if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            } else if (stock != null && stock.contains(key) && !(stock.get(key) instanceof ConfigurationSection)
                    && java.util.Objects.equals(target.get(key), stock.get(key))
                    && !java.util.Objects.equals(target.get(key), defaults.get(key))) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }
}
