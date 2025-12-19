package com.shorecrash.config;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CrashConfig {
    private final GameSettings game;
    private final HologramSettings hologram;
    private final TntHologramSettings tnt;
    private final Messages messages;
    private final StatsBookSettings statsBook;
    private final DecimalFormat moneyFormat;

    public CrashConfig(GameSettings game, HologramSettings hologram, TntHologramSettings tnt, Messages messages, StatsBookSettings statsBook, DecimalFormat moneyFormat) {
        this.game = game;
        this.hologram = hologram;
        this.tnt = tnt;
        this.messages = messages;
        this.statsBook = statsBook;
        this.moneyFormat = moneyFormat;
    }

    public static CrashConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        cfg.options().copyDefaults(true);
        plugin.saveConfig();

        GameSettings game = new GameSettings(cfg.getConfigurationSection("game"));
        HologramSettings holo = new HologramSettings(cfg.getConfigurationSection("hologram"));
        TntHologramSettings tnt = new TntHologramSettings(cfg.getConfigurationSection("tnt-hologram"));
        Messages messages = new Messages(cfg.getConfigurationSection("messages"));
        StatsBookSettings statsBook = new StatsBookSettings(cfg.getConfigurationSection("stats-book"));

        String pattern = cfg.getString("game.economy.currency-format", "#,###.##");
        DecimalFormat money = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        return new CrashConfig(game, holo, tnt, messages, statsBook, money);
    }

    public GameSettings game() {
        return game;
    }

    public HologramSettings hologram() {
        return hologram;
    }

    public TntHologramSettings tnt() {
        return tnt;
    }

    public Messages messages() {
        return messages;
    }

    public StatsBookSettings statsBook() {
        return statsBook;
    }

    public DecimalFormat moneyFormat() {
        return moneyFormat;
    }

    public static class GameSettings {
        private final long intervalMillis;
        private final long startDelayMillis;
        private final int tickInterval;
        private final double startMultiplier;
        private final double growthPerSecond;
        private final double crashVariance;
        private final double minCrashMultiplier;
        private final double maxCrashMultiplier;
        private final double minBet;
        private final double maxBet;
        private final boolean allowLateJoin;
        private final boolean economyEnabled;
        private final long actionRateLimitMs;
        private final int crashHistorySize;
        private final int lastGamesDisplayCount;

        public GameSettings(ConfigurationSection section) {
            this.intervalMillis = section.getLong("interval-seconds", 30) * 1000L;
            this.startDelayMillis = section.getLong("start-delay-seconds", 5) * 1000L;
            this.tickInterval = section.getInt("tick-interval-ticks", 2);
            this.startMultiplier = section.getDouble("start-multiplier", 1.0);
            this.growthPerSecond = section.getDouble("growth-per-second", 0.08);
            this.crashVariance = section.getDouble("crash-variance", 1.6);
            this.minCrashMultiplier = section.getDouble("min-crash-multiplier", 1.01);
            this.maxCrashMultiplier = section.getDouble("max-crash-multiplier", 1000.0);
            this.minBet = section.getDouble("min-bet", 10.0);
            this.maxBet = section.getDouble("max-bet", 20_000_000.0);
            this.allowLateJoin = section.getBoolean("allow-late-join", false);
            this.economyEnabled = section.getBoolean("economy.enabled", true);
            this.actionRateLimitMs = section.getLong("action-rate-limit-ms", 200L);
            this.crashHistorySize = section.getInt("crash-history-size", 20);
            this.lastGamesDisplayCount = section.getInt("lastgames-display-count", 5);
        }

        public long getIntervalMillis() {
            return intervalMillis;
        }

        public long getStartDelayMillis() {
            return startDelayMillis;
        }

        public int getTickInterval() {
            return tickInterval;
        }

        public double getStartMultiplier() {
            return startMultiplier;
        }

        public double getGrowthPerSecond() {
            return growthPerSecond;
        }

        public double getCrashVariance() {
            return crashVariance;
        }

        public double getMinCrashMultiplier() {
            return minCrashMultiplier;
        }

        public double getMaxCrashMultiplier() {
            return maxCrashMultiplier;
        }

        public double getMinBet() {
            return minBet;
        }

        public double getMaxBet() {
            return maxBet;
        }

        public boolean isAllowLateJoin() {
            return allowLateJoin;
        }

        public boolean isEconomyEnabled() {
            return economyEnabled;
        }

        public long getActionRateLimitMs() {
            return actionRateLimitMs;
        }

        public int getCrashHistorySize() {
            return crashHistorySize;
        }

        public int getLastGamesDisplayCount() {
            return lastGamesDisplayCount;
        }
    }

    public static class HologramSettings {
        private final boolean enabled;
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final double lineSpacing;
        private final float yaw;
        private final List<String> lines;
        private final int chartWidth;
        private final int chartHeight;
        private final double chartMaxMultiplier;
        private final String chartSymbol;
        private final String chartEmptySymbol;
        private final double chartCurveStrength;

        public HologramSettings(ConfigurationSection section) {
            this.enabled = section.getBoolean("enabled", true);
            this.world = section.getString("world", "");
            this.x = section.getDouble("x", 0.0);
            this.y = section.getDouble("y", 0.0);
            this.z = section.getDouble("z", 0.0);
            this.yaw = (float) section.getDouble("yaw", 0.0);
            this.lineSpacing = section.getDouble("line-spacing", 0.28);
            this.lines = new ArrayList<>(section.getStringList("lines"));
            ConfigurationSection chart = section.getConfigurationSection("chart");
            this.chartWidth = chart != null ? chart.getInt("width", 30) : 30;
            this.chartHeight = chart != null ? chart.getInt("height", 8) : 8;
            this.chartMaxMultiplier = chart != null ? chart.getDouble("max-multiplier", 20.0) : 20.0;
            this.chartSymbol = chart != null ? chart.getString("symbol", "/") : "/";
            this.chartEmptySymbol = chart != null ? chart.getString("empty-symbol", " ") : " ";
            this.chartCurveStrength = chart != null ? chart.getDouble("curve-strength", 2.0) : 2.0;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Location getLocation() {
            if (world == null || world.isEmpty()) {
                return null;
            }
            return new Location(Bukkit.getWorld(world), x, y, z, yaw, 0f);
        }

        public void saveLocation(JavaPlugin plugin, Location loc) {
            FileConfiguration cfg = plugin.getConfig();
            cfg.set("hologram.world", loc.getWorld().getName());
            cfg.set("hologram.x", loc.getX());
            cfg.set("hologram.y", loc.getY());
            cfg.set("hologram.z", loc.getZ());
            cfg.set("hologram.yaw", loc.getYaw());
            plugin.saveConfig();
        }

        public double getLineSpacing() {
            return lineSpacing;
        }

        public float getYaw() {
            return yaw;
        }

        public List<String> getLines() {
            return lines;
        }

        public int getChartWidth() {
            return chartWidth;
        }

        public int getChartHeight() {
            return chartHeight;
        }

        public double getChartMaxMultiplier() {
            return chartMaxMultiplier;
        }

        public String getChartSymbol() {
            return chartSymbol;
        }

        public String getChartEmptySymbol() {
            return chartEmptySymbol;
        }

        public double getChartCurveStrength() {
            return chartCurveStrength;
        }

        public String colorize(String line) {
            return ChatColor.translateAlternateColorCodes('&', line);
        }

        public List<String> colorizeLines(List<String> lines) {
            return lines.stream().map(this::colorize).collect(Collectors.toList());
        }
    }

    public static class TntHologramSettings {
        private final boolean enabled;
        private final int lifespanSeconds;
        private final String text;

        public TntHologramSettings(ConfigurationSection section) {
            this.enabled = section.getBoolean("enabled", true);
            this.lifespanSeconds = section.getInt("lifespan-seconds", 8);
            this.text = section.getString("text", "&cBOOM at &f{multiplier}x");
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getLifespanSeconds() {
            return lifespanSeconds;
        }

        public String getText() {
            return text;
        }
    }

    public static class StatsBookSettings {
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final boolean glow;

        public StatsBookSettings(ConfigurationSection section) {
            this.material = Material.matchMaterial(section != null ? section.getString("material", "WRITABLE_BOOK") : "WRITABLE_BOOK");
            this.name = section != null ? section.getString("name", "&fStats") : "&fStats";
            this.lore = section != null ? new ArrayList<>(section.getStringList("lore")) : new ArrayList<>();
            this.glow = section != null && section.getBoolean("glow", true);
        }

        public Material getMaterial() {
            return material != null ? material : Material.WRITABLE_BOOK;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public boolean isGlow() {
            return glow;
        }
    }

    public static class Messages {
        private final String prefix;
        private final String reloaded;
        private final String betPlaced;
        private final String betUpdated;
        private final String betCancelled;
        private final String betTooLow;
        private final String betTooHigh;
        private final String notWaiting;
        private final String notRunning;
        private final String alreadyIn;
        private final String noBet;
        private final String cashoutSuccess;
        private final String loss;
        private final String crashed;
        private final String start;
        private final String begin;
        private final String insufficientFunds;
        private final String vaultMissing;
        private final String holoSet;
        private final String holoCleared;
        private final String noHolo;
        private final String onlyPlayers;
        private final String noPermission;
        private final String usageCrash;
        private final String usageHolo;
        private final String invalidAmount;
        private final String rigSet;
        private final String rigTooLate;
        private final String rigTooLow;

        public Messages(ConfigurationSection section) {
            this.prefix = color(section.getString("prefix", ""));
            this.reloaded = applyPrefix(section, "reloaded", "Reloaded.");
            this.betPlaced = applyPrefix(section, "bet-placed", "Bet placed.");
            this.betUpdated = applyPrefix(section, "bet-updated", "Bet updated.");
            this.betCancelled = applyPrefix(section, "bet-cancelled", "Bet cancelled.");
            this.betTooLow = applyPrefix(section, "bet-too-low", "Bet too low.");
            this.betTooHigh = applyPrefix(section, "bet-too-high", "Bet too high.");
            this.notWaiting = applyPrefix(section, "not-waiting", "Round already running.");
            this.notRunning = applyPrefix(section, "not-running", "No round running.");
            this.alreadyIn = applyPrefix(section, "already-in", "You already joined.");
            this.noBet = applyPrefix(section, "no-bet", "You are not in this round.");
            this.cashoutSuccess = applyPrefix(section, "cashout-success", "You cashed out.");
            this.loss = applyPrefix(section, "loss", "You lost.");
            this.crashed = applyPrefix(section, "crashed", "Crashed!");
            this.start = applyPrefix(section, "start", "Starting soon.");
            this.begin = applyPrefix(section, "begin", "Multiplier live!");
            this.insufficientFunds = applyPrefix(section, "insufficient-funds", "Insufficient funds.");
            this.vaultMissing = applyPrefix(section, "vault-missing", "Vault missing.");
            this.holoSet = applyPrefix(section, "holo-set", "Hologram saved.");
            this.holoCleared = applyPrefix(section, "holo-cleared", "Hologram cleared.");
            this.noHolo = applyPrefix(section, "no-holo", "Hologram not set.");
            this.onlyPlayers = applyPrefix(section, "only-players", "Players only.");
            this.noPermission = applyPrefix(section, "no-permission", "No permission.");
            this.usageCrash = applyPrefix(section, "usage-crash", "Usage: /crash <amount>");
            this.usageHolo = applyPrefix(section, "usage-holo", "Usage: /crashholo set|clear");
            this.invalidAmount = applyPrefix(section, "invalid-amount", "Invalid amount.");
            this.rigSet = applyPrefix(section, "rig-set", "Rig set.");
            this.rigTooLate = applyPrefix(section, "rig-too-late", "Too late to rig.");
            this.rigTooLow = applyPrefix(section, "rig-too-low", "Too low.");
        }

        private static String color(String input) {
            if (input == null) {
                input = "";
            }
            String withHex = translateHexColors(input);
            return ChatColor.translateAlternateColorCodes('&', withHex);
        }

        // Support hex colors in the format &#RRGGBB
        private static String translateHexColors(String message) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < message.length(); i++) {
                char c = message.charAt(i);
                if (c == '&' && i + 7 < message.length() && message.charAt(i + 1) == '#') {
                    String hex = message.substring(i + 2, i + 8);
                    if (hex.matches("[0-9A-Fa-f]{6}")) {
                        sb.append('§').append('x');
                        for (char h : hex.toCharArray()) {
                            sb.append('§').append(Character.toLowerCase(h));
                        }
                        i += 7;
                        continue;
                    }
                }
                sb.append(c);
            }
            return sb.toString();
        }

        private String applyPrefix(ConfigurationSection section, String path, String def) {
            String raw = section.getString(path, def);
            if (raw == null) {
                raw = def;
            }
            String colored = color(raw);
            return colored.replace("%prefix%", prefix);
        }

        public String prefix() { return prefix; }
        public String reloaded() { return reloaded; }
        public String betPlaced() { return betPlaced; }
        public String betUpdated() { return betUpdated; }
        public String betCancelled() { return betCancelled; }
        public String betTooLow() { return betTooLow; }
        public String betTooHigh() { return betTooHigh; }
        public String notWaiting() { return notWaiting; }
        public String notRunning() { return notRunning; }
        public String alreadyIn() { return alreadyIn; }
        public String noBet() { return noBet; }
        public String cashoutSuccess() { return cashoutSuccess; }
        public String loss() { return loss; }
        public String crashed() { return crashed; }
        public String start() { return start; }
        public String begin() { return begin; }
        public String insufficientFunds() { return insufficientFunds; }
        public String vaultMissing() { return vaultMissing; }
        public String holoSet() { return holoSet; }
        public String holoCleared() { return holoCleared; }
        public String noHolo() { return noHolo; }
        public String onlyPlayers() { return onlyPlayers; }
        public String noPermission() { return noPermission; }
        public String usageCrash() { return usageCrash; }
        public String usageHolo() { return usageHolo; }
        public String invalidAmount() { return invalidAmount; }
        public String rigSet() { return rigSet; }
        public String rigTooLate() { return rigTooLate; }
        public String rigTooLow() { return rigTooLow; }
    }
}
