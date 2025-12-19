package com.shorecrash.game;

import com.shorecrash.config.CrashConfig;
import com.shorecrash.data.CrashDataStore;
import com.shorecrash.economy.EconomyService;
import com.shorecrash.holo.HologramManager;
import com.shorecrash.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class CrashGame {
    public enum State { WAITING, RUNNING, CRASHED }

    private final JavaPlugin plugin;
    private final CrashConfig config;
    private final HologramManager hologram;
    private final EconomyService economy;
    private final StatsService stats;
    private final CrashDataStore crashData;
    private final Random random = new Random();
    private final ArrayDeque<Double> multiplierHistory = new ArrayDeque<>();
    private final List<ItemDisplay> graphBlocks = new ArrayList<>();
    private final Map<UUID, Long> actionCooldown = new HashMap<>();
    private double lastPlottedMultiplier = 0.0;
    private long lastGraphSpawnAt = 0L;
    private long lastHologramPushAt = 0L;
    private Double riggedCrashMultiplier = null;

    private final Map<UUID, Bet> bets = new HashMap<>();
    private State state = State.WAITING;
    private BukkitRunnable task;
    private long nextStartAt;
    private long roundStartedAt;
    private long crashedAt;
    private double crashMultiplier;
    private double currentMultiplier;
    private Location graphBase;

    public CrashGame(JavaPlugin plugin, CrashConfig config, HologramManager hologram, EconomyService economy, StatsService stats, CrashDataStore crashData) {
        this.plugin = plugin;
        this.config = config;
        this.hologram = hologram;
        this.economy = economy;
        this.stats = stats;
        this.crashData = crashData;
    }

    public CrashConfig getConfigModel() {
        return config;
    }

    public void start() {
        this.graphBase = computeGraphBase();
        clearGraphBlocks();
        this.lastPlottedMultiplier = Double.NEGATIVE_INFINITY; // force immediate first emerald
        this.lastGraphSpawnAt = 0L;
        this.lastHologramPushAt = 0L;
        this.nextStartAt = System.currentTimeMillis() + config.game().getStartDelayMillis();
        this.currentMultiplier = config.game().getStartMultiplier();
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 1L, config.game().getTickInterval());

        // Spawn hologram immediately so it exists even before the first tick, e.g., after reboot
        refreshHologramNow();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        bets.clear();
        hologram.clear();
        clearGraphBlocks();
        hologram.clearCrashMarkers();
        lastPlottedMultiplier = Double.NEGATIVE_INFINITY;
        lastGraphSpawnAt = 0L;
        lastHologramPushAt = 0L;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        switch (state) {
            case WAITING -> handleWaiting(now);
            case RUNNING -> handleRunning(now);
            case CRASHED -> handleCrashed(now);
        }
        recordHistory();
        if (lastHologramPushAt == 0L || now - lastHologramPushAt >= 150L) { // throttle hologram refreshes to reduce lag
            pushHologram(now);
            lastHologramPushAt = now;
        }
        updateGraphBlocks();
    }

    public void refreshHologramNow() {
        pushHologram(System.currentTimeMillis());
        lastHologramPushAt = System.currentTimeMillis();
    }

    private void handleWaiting(long now) {
        if (now >= nextStartAt) {
            beginRound(now);
        }
    }

    private void handleRunning(long now) {
        double elapsedSeconds = (now - roundStartedAt) / 1000.0;
        currentMultiplier = config.game().getStartMultiplier() * Math.exp(config.game().getGrowthPerSecond() * elapsedSeconds);
        if (currentMultiplier >= crashMultiplier) {
            crash(now);
        }
    }

    private void handleCrashed(long now) {
        long resumeAt = crashedAt + config.game().getStartDelayMillis();
        if (now >= resumeAt) {
            resetToWaiting(now);
        }
    }

    private void beginRound(long now) {
        state = State.RUNNING;
        roundStartedAt = now;
        crashMultiplier = riggedCrashMultiplier != null ? riggedCrashMultiplier : sampleCrashMultiplier();
        riggedCrashMultiplier = null;
        currentMultiplier = config.game().getStartMultiplier();
        nextStartAt = roundStartedAt + config.game().getIntervalMillis();
    }

    private void crash(long now) {
        state = State.CRASHED;
        crashedAt = now;
        bets.values().stream()
                .filter(b -> b.getStatus() == Bet.Status.ACTIVE)
                .forEach(this::markLostWithMessage);
        Location markerLoc = null;
        if (!graphBlocks.isEmpty()) {
            Location last = graphBlocks.get(graphBlocks.size() - 1).getLocation();
            if (last != null && last.getWorld() != null) {
                markerLoc = last.clone();
            }
        }
        if (markerLoc == null) {
            markerLoc = config.hologram().getLocation();
        }
        if (markerLoc != null) {
            String text = config.tnt().getText().replace("{multiplier}", formatMultiplier(crashMultiplier));
            hologram.spawnCrashMarker(markerLoc, text, config.tnt().getLifespanSeconds());
        }
        recordCrashResult(crashMultiplier);
    }

    private void resetToWaiting(long now) {
        state = State.WAITING;
        bets.clear();
        multiplierHistory.clear();
        clearGraphBlocks();
        hologram.clearCrashMarkers();
        lastPlottedMultiplier = Double.NEGATIVE_INFINITY; // allow immediate spawn next round
        lastGraphSpawnAt = 0L;
        lastHologramPushAt = 0L;
        if (nextStartAt < now + 1000L) {
            nextStartAt = now + config.game().getIntervalMillis();
        }
    }

    private double sampleCrashMultiplier() {
        double roll = random.nextDouble();
        if (roll < 0.02) {
            return 1.00; // 2% chance to crash instantly at 1.00x
        }
        double value = config.game().getMinCrashMultiplier() + (-Math.log(1.0 - roll) * config.game().getCrashVariance());
        return Math.min(value, config.game().getMaxCrashMultiplier());
    }

    public boolean tryRigNextCrash(Player admin, double multiplier) {
        if (state != State.WAITING) {
            send(admin, config.messages().rigTooLate());
            return false;
        }
        if (multiplier < config.game().getMinCrashMultiplier()) {
            send(admin, config.messages().rigTooLow().replace("{min}", formatMultiplier(config.game().getMinCrashMultiplier())));
            return false;
        }
        double capped = Math.min(multiplier, config.game().getMaxCrashMultiplier());
        this.riggedCrashMultiplier = capped;
        send(admin, config.messages().rigSet().replace("{multiplier}", formatMultiplier(capped)));
        return true;
    }

    public boolean placeBet(Player player, double amount) {
        if (isRateLimited(player)) {
            return false;
        }
        if (state != State.WAITING && !config.game().isAllowLateJoin()) {
            send(player, config.messages().notWaiting());
            return false;
        }

        Bet existing = bets.get(player.getUniqueId());
        double baseAmount = existing != null ? existing.getAmount() : 0.0;
        boolean stackingWhileWaiting = state == State.WAITING && existing != null;

        if (!stackingWhileWaiting && amount < config.game().getMinBet()) {
            send(player, config.messages().betTooLow().replace("{min}", formatMoney(config.game().getMinBet())));
            return false;
        }

        double targetAmount = stackingWhileWaiting ? baseAmount + amount : amount;
        if (targetAmount > config.game().getMaxBet()) {
            send(player, config.messages().betTooHigh().replace("{max}", formatMoney(config.game().getMaxBet())));
            return false;
        }

        if (existing != null && state != State.WAITING && targetAmount < baseAmount) {
            send(player, config.messages().notWaiting());
            return false; // avoid mid-round bet reductions when late-join is enabled
        }

        double delta = targetAmount - baseAmount;
        if (economy.isEnabled() && delta > 0 && !economy.withdraw(player, delta)) {
            send(player, config.messages().insufficientFunds());
            return false;
        }
        if (economy.isEnabled() && delta < 0 && state == State.WAITING) {
            economy.deposit(player, -delta); // refund if the target bet is lower during the waiting phase
        }

        if (existing == null) {
            Bet bet = new Bet(player.getUniqueId(), player.getName(), targetAmount);
            bets.put(player.getUniqueId(), bet);
            send(player, config.messages().betPlaced().replace("{amount}", formatMoney(targetAmount)));
        } else {
            existing.setAmount(targetAmount);
            send(player, config.messages().betUpdated().replace("{amount}", formatMoney(targetAmount)));
        }
        return true;
    }

    public void cancelBet(Player player) {
        if (isRateLimited(player)) {
            return;
        }
        Bet bet = bets.get(player.getUniqueId());
        if (bet == null || bet.getStatus() != Bet.Status.ACTIVE) {
            send(player, config.messages().noBet());
            return;
        }
        if (state != State.WAITING) {
            send(player, config.messages().notWaiting());
            return;
        }
        double amount = bet.getAmount();
        bets.remove(player.getUniqueId());
        if (economy.isEnabled()) {
            economy.deposit(player, amount);
        }
        send(player, config.messages().betCancelled().replace("{amount}", formatMoney(amount)));
    }

    public void handleQuit(Player player) {
        Bet bet = bets.get(player.getUniqueId());
        if (bet == null) {
            return;
        }

        if (state == State.WAITING) {
            // Safe to refund while waiting to avoid locking wagers
            bets.remove(player.getUniqueId());
            if (economy.isEnabled()) {
                economy.deposit(player, bet.getAmount());
            }
            return;
        }

        if (state == State.RUNNING && bet.getStatus() == Bet.Status.ACTIVE) {
            markLostSilently(bet);
        }
    }

    public void cashout(Player player) {
        if (isRateLimited(player)) {
            return;
        }
        if (state != State.RUNNING) {
            send(player, config.messages().notRunning());
            return;
        }
        Bet bet = bets.get(player.getUniqueId());
        if (bet == null || bet.getStatus() != Bet.Status.ACTIVE) {
            send(player, config.messages().noBet());
            return;
        }
        double payout = bet.getAmount() * currentMultiplier;
        double houseEdge = payout * 0.01;
        double finalPayout = payout - houseEdge;
        bet.markCashed(currentMultiplier, finalPayout);
        economy.deposit(player, finalPayout);
        stats.recordCashout(player.getUniqueId(), bet.getAmount(), finalPayout);
        // House edge: keep 1% rake; nothing else to do since we withheld it from payout
        send(player, config.messages().cashoutSuccess()
            .replace("{payout}", formatMoney(finalPayout))
                .replace("{multiplier}", formatMultiplier(currentMultiplier)));
    }

    private void send(Player player, String message) {
        player.sendMessage(message);
    }

    private void markLostWithMessage(Bet bet) {
        bet.markLost();
        Player player = Bukkit.getPlayer(bet.getPlayerId());
        stats.recordLoss(bet.getPlayerId(), bet.getAmount());
        if (player == null) {
            return;
        }
        send(player, config.messages().loss()
                .replace("{amount}", formatMoney(bet.getAmount()))
                .replace("{multiplier}", formatMultiplier(crashMultiplier)));
    }

    private void markLostSilently(Bet bet) {
        bet.markLost();
        stats.recordLoss(bet.getPlayerId(), bet.getAmount());
    }

    private void updateGraphBlocks() {
        if (state != State.RUNNING) {
            lastGraphSpawnAt = 0L;
            return;
        }
        if (graphBase == null || graphBase.getWorld() == null) {
            graphBase = computeGraphBase();
            if (graphBase == null || graphBase.getWorld() == null) {
                clearGraphBlocks();
                return;
            }
        }
        Vector axis = graphAxis(graphBase);
        int width = Math.max(4, config.hologram().getChartWidth());
        double spacing = 0.10; // slightly tighter spacing for smoother travel
        long now = System.currentTimeMillis();
        final long spawnIntervalMs = 250L; // faster updates for smoother motion

        if (lastGraphSpawnAt != 0L && now - lastGraphSpawnAt < spawnIntervalMs) {
            return; // wait for next tick to spawn
        }

        World world = graphBase.getWorld();

        if (graphBlocks.size() >= width) {
            clearGraphBlocks();
        }

        // Keep Y growth simple and unrelated to crash progress to avoid predictability
        double yOffset = 0.10 + (graphBlocks.size() * 0.05);
        Location loc = graphBase.clone()
            .add(axis.clone().multiply(graphBlocks.size() * spacing))
            .add(0, yOffset, 0);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.EMERALD_BLOCK));
        display.setTransformation(smallTransform());
        display.setBillboard(ItemDisplay.Billboard.FIXED);
        display.setRotation(graphBase.getYaw(), 0f);
        display.setPersistent(false);
        graphBlocks.add(display);

        // Re-align all blocks to their index to avoid visible jumps when pruning
        for (int i = 0; i < graphBlocks.size(); i++) {
            ItemDisplay block = graphBlocks.get(i);
            double offsetY = 0.10 + (i * 0.05);
            Location target = graphBase.clone()
                    .add(axis.clone().multiply(i * spacing))
                    .add(0, offsetY, 0);
            block.teleport(target);
            block.setRotation(graphBase.getYaw(), 0f);
        }

        lastPlottedMultiplier = currentMultiplier;
        lastGraphSpawnAt = now;
    }

    private double computeHeight(double multiplier) {
        double max = Math.max(0.0001, config.hologram().getChartMaxMultiplier());
        double scaled = Math.log1p(multiplier) / Math.log1p(max); // 0..1
        scaled = Math.max(0.0, Math.min(1.0, scaled));
        return 0.5 + scaled * 3.5; // baseline lift plus visible rise
    }

    private Transformation smallTransform() {
        Vector3f translation = new Vector3f(0f, 0f, 0f);
        Vector3f scale = new Vector3f(0.30f, 0.30f, 0.30f); // scale to 0.3 as requested
        Quaternionf identity = new Quaternionf();
        return new Transformation(translation, identity, scale, identity);
    }

    private Location computeGraphBase() {
        Location base = config.hologram().getLocation();
        if (base == null || base.getWorld() == null) {
            return null;
        }
        double spacing = config.hologram().getLineSpacing();
        int lineCount = Math.max(0, config.hologram().getLines().size());
        double drop = spacing * (lineCount + 4); // raise the graph closer to the hologram text
        Vector axis = graphAxis(base);
        Location start = base.clone().add(axis.clone().multiply(-1.5)).add(0, -drop, 0);
        start.setYaw(base.getYaw());
        return start;
    }

    private void clearGraphBlocks() {
        graphBlocks.forEach(ItemDisplay::remove);
        graphBlocks.clear();
    }

    private Vector graphAxis(Location base) {
        double yawRad = Math.toRadians(base.getYaw());
        // Use the player's facing to rotate the horizontal axis so the emerald graph aligns with the hologram
        return new Vector(-Math.cos(yawRad), 0, -Math.sin(yawRad));
    }

    private void pushHologram(long now) {
        List<String> templates = config.hologram().getLines();
        List<String> rendered = new ArrayList<>(templates.size());
        for (String line : templates) {
            String out = line
                    .replace("{state}", stateText())
                    .replace("{timer}", state == State.RUNNING ? "" : String.valueOf(Math.max(0, (nextStartAt - now) / 1000)))
                    .replace("{multiplier}", formatMultiplier(currentMultiplier))
                    .replace("{pot}", formatMoneyShort(potTotal()))
                    .replace("{players_total}", String.valueOf(activePlayerCount()))
                    .replace("{players}", renderPlayers());

            if (out.contains("{chart}")) {
                List<String> chartLines = buildChartLines();
                String first = out.replace("{chart}", chartLines.isEmpty() ? "" : chartLines.get(0));
                rendered.add(first);
                for (int i = 1; i < chartLines.size(); i++) {
                    rendered.add(chartLines.get(i));
                }
            } else {
                rendered.add(out);
            }
        }
        hologram.refresh(rendered);
    }

    private String stateText() {
        return switch (state) {
            case WAITING -> ChatColor.YELLOW + "Waiting";
            case RUNNING -> ChatColor.GREEN + "Live";
            case CRASHED -> ChatColor.RED + "Crashed";
        };
    }

    private double potTotal() {
        return bets.values().stream()
                .filter(b -> b.getStatus() == Bet.Status.ACTIVE)
                .mapToDouble(Bet::getAmount)
                .sum();
    }

    private int activePlayerCount() {
        return (int) bets.values().stream()
                .filter(b -> b.getStatus() == Bet.Status.ACTIVE)
                .count();
    }

    private String renderPlayers() {
        List<Bet> visible = bets.values().stream()
                .filter(b -> b.getStatus() == Bet.Status.ACTIVE || b.getStatus() == Bet.Status.CASHED_OUT)
                .sorted((a, b) -> Integer.compare(statusRank(a.getStatus()), statusRank(b.getStatus())))
                .limit(3)
                .collect(Collectors.toList());

        if (visible.isEmpty()) {
            return "None";
        }

        return visible.stream()
                .map(this::formatBetEntry)
                .collect(Collectors.joining(", "));
    }

    private int statusRank(Bet.Status status) {
        return switch (status) {
            case ACTIVE -> 0;
            case CASHED_OUT -> 1;
            case LOST -> 2;
        };
    }

    private String formatBetEntry(Bet bet) {
        return switch (bet.getStatus()) {
            case ACTIVE -> bet.getPlayerName() + "(" + formatMoneyShort(bet.getAmount()) + ChatColor.RESET + ")";
            case CASHED_OUT -> ChatColor.GRAY + bet.getPlayerName() + " @ " + ChatColor.GREEN + formatMultiplier(bet.getCashoutMultiplier()) + "x" + ChatColor.RESET;
            case LOST -> bet.getPlayerName();
        };
    }

    private List<String> buildChartLines() {
        // Disable ASCII chart since we render the 3D emerald graph instead
        return Collections.emptyList();
    }

    private void recordHistory() {
        int width = Math.max(4, config.hologram().getChartWidth());
        double sample;
        if (state == State.RUNNING) {
            sample = currentMultiplier;
        } else if (state == State.CRASHED) {
            sample = crashMultiplier;
        } else {
            sample = config.game().getStartMultiplier();
        }
        if (multiplierHistory.size() >= width) {
            multiplierHistory.pollFirst();
        }
        multiplierHistory.addLast(sample);
    }

    private void recordCrashResult(double result) {
        crashData.record(result);
    }

    public void sendLastGames(Player player) {
        List<Double> recent = crashData.recent(Math.max(1, config.game().getLastGamesDisplayCount()));
        if (recent.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No previous games yet.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GRAY).append("Last games: ");
        for (int i = 0; i < recent.size(); i++) {
            double val = recent.get(i);
            ChatColor color = colorForMultiplier(val);
            sb.append(color).append(formatMultiplier(val)).append("x");
            if (i < recent.size() - 1) {
                sb.append(ChatColor.GRAY).append(" | ");
            }
        }
        player.sendMessage(sb.toString());
    }

    private ChatColor colorForMultiplier(double m) {
        if (m < 1.25) {
            return ChatColor.RED;
        }
        if (m < 2.0) {
            return ChatColor.YELLOW;
        }
        if (m <= 5.0) {
            return ChatColor.GREEN;
        }
        return ChatColor.AQUA;
    }

    private boolean isRateLimited(Player player) {
        long now = System.currentTimeMillis();
        Long last = actionCooldown.get(player.getUniqueId());
        long cooldown = Math.max(0L, config.game().getActionRateLimitMs());
        if (last != null && now - last < cooldown) {
            return true;
        }
        actionCooldown.put(player.getUniqueId(), now);
        return false;
    }

    public void openPlayerStatsGui(Player viewer, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String displayName = target.getName() == null ? targetName : target.getName();
        Inventory inv = buildStatsInventory(displayName, stats.get(target.getUniqueId()));
        viewer.openInventory(inv);
    }

    public void openServerSummaryGui(Player viewer) {
        Inventory inv = buildStatsInventory("Server", stats.getTotals());
        viewer.openInventory(inv);
    }

    private Inventory buildStatsInventory(String playerName, StatsService.PlayerStats data) {
        CrashConfig.StatsBookSettings cfg = config.statsBook();
        int size = 27; // 3 rows for centered display
        InventoryHolder holder = new com.shorecrash.stats.StatsInventoryHolder(playerName);
        Inventory inv = Bukkit.createInventory(holder, size, color(cfg.getName().replace("%player%", playerName)));

        // fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.setDisplayName(" ");
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        ItemStack statsItem = buildStatsItem(playerName, data);
        inv.setItem(13, statsItem); // center slot
        return inv;
    }

    private ItemStack buildStatsItem(String playerName, StatsService.PlayerStats data) {
        CrashConfig.StatsBookSettings cfg = config.statsBook();
        ItemStack item = new ItemStack(cfg.getMaterial() == null ? Material.WRITABLE_BOOK : cfg.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(cfg.getName().replace("%player%", playerName)));
        List<String> lore = cfg.getLore().stream()
                .map(line -> replaceStatsPlaceholders(line, playerName, data))
                .map(this::color)
                .collect(Collectors.toList());
        meta.setLore(lore);

        if (cfg.isGlow()) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true); // harmless glow trigger
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private String replaceStatsPlaceholders(String line, String playerName, StatsService.PlayerStats data) {
        long wins = data.getWins();
        long losses = data.getLosses();
        long totalGames = data.getTotalGames();
        double winRate = totalGames > 0 ? (wins * 100.0 / totalGames) : 0.0;

        return line
                .replace("%player%", playerName)
                .replace("%wins%", String.valueOf(wins))
                .replace("%losses%", String.valueOf(losses))
                .replace("%total_games%", String.valueOf(totalGames))
                .replace("%win_rate%", String.format("%.2f%%", winRate))
                .replace("%net%", formatMoneyCompact(data.getNet()))
                .replace("%profit%", formatMoneyCompact(data.getProfit()))
                .replace("%loss%", formatMoneyCompact(data.getLoss()))
                .replace("%total_bet%", formatMoneyCompact(data.getTotalBet()))
                .replace("%total_won%", formatMoneyCompact(data.getTotalWon()));
    }

    private String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', translateHexColors(input));
    }

    private String translateHexColors(String message) {
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

    private String formatMultiplier(double value) {
        return String.format("%.2f", value);
    }

    private String formatMoney(double value) {
        return config.moneyFormat().format(value);
    }

    private String formatMoneyCompact(double value) {
        double abs = Math.abs(value);
        String suffix = "";
        double scaled = value;
        if (abs >= 1_000_000_000) {
            suffix = "b";
            scaled = value / 1_000_000_000d;
        } else if (abs >= 1_000_000) {
            suffix = "m";
            scaled = value / 1_000_000d;
        } else if (abs >= 1_000) {
            suffix = "k";
            scaled = value / 1_000d;
        }
        String formatted = String.format("%.2f", scaled);
        if (formatted.endsWith(".00")) {
            formatted = formatted.substring(0, formatted.length() - 3);
        }
        return formatted + suffix;
    }

    private String formatMoneyShort(double value) {
        String suffix;
        double scaled;
        if (value >= 1_000_000_000) {
            suffix = "b";
            scaled = value / 1_000_000_000d;
        } else if (value >= 1_000_000) {
            suffix = "m";
            scaled = value / 1_000_000d;
        } else if (value >= 1_000) {
            suffix = "k";
            scaled = value / 1_000d;
        } else {
            return ChatColor.GREEN + "$" + formatMoney(value) + ChatColor.RESET;
        }
        String formatted = String.format("%.1f", scaled);
        // Trim trailing .0
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }
        return ChatColor.GREEN + "$" + formatted + suffix + ChatColor.RESET;
    }
}
