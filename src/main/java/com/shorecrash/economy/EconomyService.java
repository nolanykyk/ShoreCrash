package com.shorecrash.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

/**
 * Thin wrapper around Vault economy to allow the plugin to run without Vault present.
 */
public class EconomyService {
    private final boolean enabled;
    private final Economy economy;
    private final DecimalFormat formatter;

    public EconomyService(boolean enabled, Economy economy, DecimalFormat formatter) {
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
        if (economy.getBalance(player) < amount) {
            return false;
        }
        economy.withdrawPlayer(player, amount);
        return true;
    }

    public void deposit(Player player, double amount) {
        if (!enabled) {
            return;
        }
        economy.depositPlayer(player, amount);
    }
}
