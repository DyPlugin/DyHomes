package dev.dyhomes.economy;

import dev.dyhomes.DyHomes;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;

/**
 * Optional prices through Vault. Without Vault or a registered economy provider every action stays free,
 * so a missing provider never locks players out of their homes.
 */
public class EconomyService {
    /** Actions that can have a price, matching the keys under economy.prices. */
    public enum Action { HOME, WARP, SPAWN, SETHOME }

    private final DyHomes plugin;
    private volatile Economy economy;

    public EconomyService(DyHomes plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        economy = provider();
        if (enabled() && economy == null)
            plugin.getLogger().warning("Prices are enabled, but Vault or an economy provider is missing; every action stays free.");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("economy.enabled", false);
    }

    /** The price a player pays for an action; 0 when prices are off, unavailable or bypassed. */
    public double price(Player player, Action action) {
        if (!enabled() || player.hasPermission("dyhomes.bypass.economy")) return 0;
        double amount = plugin.getConfig().getDouble("economy.prices." + action.name().toLowerCase(Locale.ROOT), 0);
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        return available() ? amount : 0;
    }

    public boolean available() {
        // Requery so a provider enabled or disabled after DyHomes is observed.
        economy = provider();
        return economy != null;
    }

    public boolean has(Player player, double amount) {
        if (amount <= 0) return true;
        Economy provider = economy;
        try {
            return provider != null && provider.has(player, amount);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Economy balance query failed: " + exception.getMessage());
            return false;
        }
    }

    /** Takes the amount; null when it could not be taken. A zero amount always succeeds. */
    public Withdrawal withdraw(Player player, double amount) {
        if (amount <= 0) return new Withdrawal(player, null, 0);
        Economy provider = economy;
        if (provider == null || !has(player, amount)) return null;
        try {
            EconomyResponse response = provider.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess() ? new Withdrawal(player, provider, amount) : null;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Economy withdrawal result is uncertain for " + player.getUniqueId()
                    + "; manual reconciliation required: " + exception.getMessage());
            return null;
        }
    }

    public String format(double amount) {
        Economy provider = economy;
        if (provider != null) {
            try {
                return provider.format(amount);
            } catch (RuntimeException ignored) {
                // Fall through to a plain number.
            }
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private Economy provider() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return null;
        try {
            RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            return registration == null ? null : registration.getProvider();
        } catch (LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public final class Withdrawal {
        private final Player player;
        private final Economy provider;
        private final double amount;
        private boolean refunded;

        private Withdrawal(Player player, Economy provider, double amount) {
            this.player = player;
            this.provider = provider;
            this.amount = amount;
        }

        public double amount() {
            return amount;
        }

        /** Gives the money back once; a failed or uncertain refund is logged for the server owner. */
        public boolean refund() {
            if (refunded) return false;
            refunded = true;
            if (provider == null || amount == 0) return true;
            try {
                EconomyResponse response = provider.depositPlayer(player, amount);
                if (response != null && response.transactionSuccess()) return true;
            } catch (RuntimeException ignored) {
                // Logged below.
            }
            plugin.getLogger().severe("Refund failed or is uncertain for " + player.getUniqueId() + " (" + amount
                    + "); manual reconciliation required.");
            return false;
        }
    }
}
