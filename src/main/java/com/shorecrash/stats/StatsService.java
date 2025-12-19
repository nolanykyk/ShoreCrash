package com.shorecrash.stats;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsService {
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final PlayerStats serverTotals = new PlayerStats();
    private final File file;

    public StatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public PlayerStats get(UUID id) {
        return playerStats.computeIfAbsent(id, k -> new PlayerStats());
    }

    public PlayerStats getTotals() {
        return serverTotals;
    }

    public void recordCashout(UUID id, double betAmount, double payout) {
        PlayerStats stats = get(id);
        stats.recordCashout(betAmount, payout);
        serverTotals.recordCashout(betAmount, payout);
        save();
    }

    public void recordLoss(UUID id, double betAmount) {
        PlayerStats stats = get(id);
        stats.recordLoss(betAmount);
        serverTotals.recordLoss(betAmount);
        save();
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            String base = "players." + entry.getKey();
            PlayerStats ps = entry.getValue();
            cfg.set(base + ".wins", ps.getWins());
            cfg.set(base + ".losses", ps.getLosses());
            cfg.set(base + ".totalGames", ps.getTotalGames());
            cfg.set(base + ".net", ps.getNet());
            cfg.set(base + ".profit", ps.getProfit());
            cfg.set(base + ".loss", ps.getLoss());
            cfg.set(base + ".totalBet", ps.getTotalBet());
            cfg.set(base + ".totalWon", ps.getTotalWon());
        }
        // Server totals stored under special key
        cfg.set("server.wins", serverTotals.getWins());
        cfg.set("server.losses", serverTotals.getLosses());
        cfg.set("server.totalGames", serverTotals.getTotalGames());
        cfg.set("server.net", serverTotals.getNet());
        cfg.set("server.profit", serverTotals.getProfit());
        cfg.set("server.loss", serverTotals.getLoss());
        cfg.set("server.totalBet", serverTotals.getTotalBet());
        cfg.set("server.totalWon", serverTotals.getTotalWon());
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stats.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.getConfigurationSection("players") != null) {
            for (String key : cfg.getConfigurationSection("players").getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            PlayerStats ps = new PlayerStats();
            ps.setWins(cfg.getLong("players." + key + ".wins", 0));
            ps.setLosses(cfg.getLong("players." + key + ".losses", 0));
            ps.setTotalGames(cfg.getLong("players." + key + ".totalGames", 0));
            ps.setNet(cfg.getDouble("players." + key + ".net", 0));
            ps.setProfit(cfg.getDouble("players." + key + ".profit", 0));
            ps.setLoss(cfg.getDouble("players." + key + ".loss", 0));
            ps.setTotalBet(cfg.getDouble("players." + key + ".totalBet", 0));
            ps.setTotalWon(cfg.getDouble("players." + key + ".totalWon", 0));
            playerStats.put(id, ps);
        }
        }
        serverTotals.setWins(cfg.getLong("server.wins", 0));
        serverTotals.setLosses(cfg.getLong("server.losses", 0));
        serverTotals.setTotalGames(cfg.getLong("server.totalGames", 0));
        serverTotals.setNet(cfg.getDouble("server.net", 0));
        serverTotals.setProfit(cfg.getDouble("server.profit", 0));
        serverTotals.setLoss(cfg.getDouble("server.loss", 0));
        serverTotals.setTotalBet(cfg.getDouble("server.totalBet", 0));
        serverTotals.setTotalWon(cfg.getDouble("server.totalWon", 0));
    }

    public static class PlayerStats {
        private long wins;
        private long losses;
        private long totalGames;
        private double net;
        private double profit;
        private double loss;
        private double totalBet;
        private double totalWon;

        public void recordCashout(double betAmount, double payout) {
            wins++;
            totalGames++;
            totalBet += betAmount;
            totalWon += payout;
            double delta = payout - betAmount;
            net += delta;
            if (delta >= 0) {
                profit += delta;
            }
        }

        public void recordLoss(double betAmount) {
            losses++;
            totalGames++;
            totalBet += betAmount;
            loss += betAmount;
            net -= betAmount;
        }

        public long getWins() { return wins; }
        public long getLosses() { return losses; }
        public long getTotalGames() { return totalGames; }
        public double getNet() { return net; }
        public double getProfit() { return profit; }
        public double getLoss() { return loss; }
        public double getTotalBet() { return totalBet; }
        public double getTotalWon() { return totalWon; }

        public void setWins(long wins) { this.wins = wins; }
        public void setLosses(long losses) { this.losses = losses; }
        public void setTotalGames(long totalGames) { this.totalGames = totalGames; }
        public void setNet(double net) { this.net = net; }
        public void setProfit(double profit) { this.profit = profit; }
        public void setLoss(double loss) { this.loss = loss; }
        public void setTotalBet(double totalBet) { this.totalBet = totalBet; }
        public void setTotalWon(double totalWon) { this.totalWon = totalWon; }
    }
}
