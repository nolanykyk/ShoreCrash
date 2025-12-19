package com.shorecrash;

import com.shorecrash.command.CrashCommand;
import com.shorecrash.command.CrashAdminCommand;
import com.shorecrash.command.HologramCommand;
import com.shorecrash.command.ReloadCommand;
import com.shorecrash.config.CrashConfig;
import com.shorecrash.data.CrashDataStore;
import com.shorecrash.economy.EconomyService;
import com.shorecrash.game.CrashGame;
import com.shorecrash.holo.HologramManager;
import com.shorecrash.listener.CrashListener;
import com.shorecrash.stats.StatsService;
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
    private StatsService statsService;
    private CrashDataStore crashDataStore;

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
        if (statsService != null) {
            statsService.save();
        }
        if (crashDataStore != null) {
            crashDataStore.save();
        }
    }

    public void reloadAndBoot() {
        if (crashGame != null) {
            crashGame.stop();
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        reloadConfig();
        this.configModel = CrashConfig.load(this);
        this.economyService = setupEconomy(configModel.moneyFormat());
        this.hologramManager = new HologramManager(this, configModel);
        this.statsService = new StatsService(this);
        this.crashDataStore = new CrashDataStore(this);
        this.crashDataStore.setMaxSize(configModel.game().getCrashHistorySize());
        this.crashGame = new CrashGame(this, configModel, hologramManager, economyService, statsService, crashDataStore);
        this.hologramManager.cleanupOrphans();
        registerCommands();
        registerListeners();
        crashGame.start();
    }

    private void registerCommands() {
        CrashCommand crash = new CrashCommand(crashGame, statsService);
        HologramCommand holo = new HologramCommand(this);
        ReloadCommand reload = new ReloadCommand(this);
        CrashAdminCommand admin = new CrashAdminCommand(crashGame);

        if (getCommand("crash") != null) {
            getCommand("crash").setExecutor(crash);
            getCommand("crash").setTabCompleter(crash);
        }
        if (getCommand("crashholo") != null) {
            getCommand("crashholo").setExecutor(holo);
            getCommand("crashholo").setTabCompleter(holo);
        }
        if (getCommand("crashreload") != null) {
            getCommand("crashreload").setExecutor(reload);
            getCommand("crashreload").setTabCompleter(reload);
        }
        if (getCommand("crashadmin") != null) {
            getCommand("crashadmin").setExecutor(admin);
            getCommand("crashadmin").setTabCompleter(admin);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new CrashListener(crashGame), this);
    }

    private EconomyService setupEconomy(DecimalFormat moneyFormat) {
        if (!configModel.game().isEconomyEnabled()) {
            return new EconomyService(this, false, null, moneyFormat);
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault not found; economy disabled.");
            return new EconomyService(this, false, null, moneyFormat);
        }
        return new EconomyService(this, true, rsp.getProvider(), moneyFormat);
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
