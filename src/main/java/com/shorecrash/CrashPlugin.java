package com.shorecrash;

import com.shorecrash.command.CashoutCommand;
import com.shorecrash.command.CrashCommand;
import com.shorecrash.command.CrashAdminCommand;
import com.shorecrash.command.HologramCommand;
import com.shorecrash.command.ReloadCommand;
import com.shorecrash.config.CrashConfig;
import com.shorecrash.economy.EconomyService;
import com.shorecrash.game.CrashGame;
import com.shorecrash.holo.HologramManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;

public class CrashPlugin extends JavaPlugin {
    private CrashConfig configModel;
    private EconomyService economyService;
    private HologramManager hologramManager;
    private CrashGame crashGame;

    @Override
    public void onEnable() {
        reloadAndBoot();
        getLogger().info("ShoreCrash enabled.");
    }

    @Override
    public void onDisable() {
        if (crashGame != null) {
            crashGame.stop();
        }
    }

    public void reloadAndBoot() {
        if (crashGame != null) {
            crashGame.stop();
        }
        reloadConfig();
        this.configModel = CrashConfig.load(this);
        this.economyService = setupEconomy(configModel.moneyFormat());
        this.hologramManager = new HologramManager(this, configModel);
        this.crashGame = new CrashGame(this, configModel, hologramManager, economyService);
        registerCommands();
        crashGame.start();
    }

    private void registerCommands() {
        getCommand("crash").setExecutor(new CrashCommand(crashGame));
        getCommand("crashcashout").setExecutor(new CashoutCommand(crashGame));
        getCommand("crashholo").setExecutor(new HologramCommand(this));
        getCommand("crashreload").setExecutor(new ReloadCommand(this));
        getCommand("crashadmin").setExecutor(new CrashAdminCommand(crashGame));
    }

    private EconomyService setupEconomy(DecimalFormat moneyFormat) {
        if (!configModel.game().isEconomyEnabled()) {
            return new EconomyService(false, null, moneyFormat);
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault not found; economy disabled.");
            return new EconomyService(false, null, moneyFormat);
        }
        return new EconomyService(true, rsp.getProvider(), moneyFormat);
    }

    public CrashConfig getConfigModel() {
        return configModel;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public CrashGame getCrashGame() {
        return crashGame;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }
}
