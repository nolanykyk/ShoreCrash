package com.shorecrash.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;

/**
 * Thin wrapper around Vault economy to allow the plugin to run without Vault present.
 */
public class EconomyService {
    private final boolean enabled;
    private final Economy economy;
    private final DecimalFormat formatter;
    private final JavaPlugin plugin;

    public EconomyService(JavaPlugin plugin, boolean enabled, Economy economy, DecimalFormat formatter) {
        this.plugin = plugin;
        this.enabled = enabled && economy != null;
        this.economy = economy;
        this.formatter = formatter;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String format(double amount) {
        return formatter.format(amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!enabled) {
            return true;
        }
        if (economy == null) {
            plugin.getLogger().warning("Economy unavailable during withdraw; denying transaction.");
            return false;
        }
        try {
            if (economy.getBalance(player) < amount) {
                return false;
            }
            economy.withdrawPlayer(player, amount);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Economy withdraw failed for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    public void deposit(Player player, double amount) {
        if (!enabled) {
            return;
        }
        if (economy == null) {
            plugin.getLogger().warning("Economy unavailable during deposit; funds not returned for " + player.getName());
            return;
        }
        try {
            economy.depositPlayer(player, amount);
        } catch (Exception ex) {
            plugin.getLogger().warning("Economy deposit failed for " + player.getName() + ": " + ex.getMessage());
        }
    }
}
