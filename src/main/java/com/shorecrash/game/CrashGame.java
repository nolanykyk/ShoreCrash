package com.shorecrash.game;

import com.shorecrash.config.CrashConfig;
import com.shorecrash.economy.EconomyService;
import com.shorecrash.holo.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
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
    private final Random random = new Random();
    private final ArrayDeque<Double> multiplierHistory = new ArrayDeque<>();
    private final List<ItemDisplay> graphBlocks = new ArrayList<>();
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

    public CrashGame(JavaPlugin plugin, CrashConfig config, HologramManager hologram, EconomyService economy) {
        this.plugin = plugin;
        this.config = config;
        this.hologram = hologram;
        this.economy = economy;
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
        if (state != State.WAITING && !config.game().isAllowLateJoin()) {
            send(player, config.messages().notWaiting());
            return false;
        }
        if (amount < config.game().getMinBet()) {
            send(player, config.messages().betTooLow().replace("{min}", formatMoney(config.game().getMinBet())));
            return false;
        }
        if (amount > config.game().getMaxBet()) {
            send(player, config.messages().betTooHigh().replace("{max}", formatMoney(config.game().getMaxBet())));
            return false;
        }
        if (economy.isEnabled() && !economy.withdraw(player, amount)) {
            send(player, config.messages().insufficientFunds());
            return false;
        }
        Bet bet = bets.get(player.getUniqueId());
        if (bet == null) {
            bet = new Bet(player.getUniqueId(), player.getName(), amount);
            bets.put(player.getUniqueId(), bet);
            send(player, config.messages().betPlaced().replace("{amount}", formatMoney(amount)));
        } else {
            bet.setAmount(amount);
            send(player, config.messages().betUpdated().replace("{amount}", formatMoney(amount)));
        }
        return true;
    }

    public void cancelBet(Player player) {
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

    public void cashout(Player player) {
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
        if (player == null) {
            return;
        }
        send(player, config.messages().loss()
                .replace("{amount}", formatMoney(bet.getAmount()))
                .replace("{multiplier}", formatMultiplier(crashMultiplier)));
    }

    private void updateGraphBlocks() {
        if (state != State.RUNNING) {
            lastGraphSpawnAt = 0L;
            return;
        }
        if (graphBase == null || graphBase.getWorld() == null) {
            return;
        }
        int width = Math.max(4, config.hologram().getChartWidth());
        double spacing = 0.12; // tighter spacing to reduce horizontal travel
        long now = System.currentTimeMillis();
        final long spawnIntervalMs = 400L; // constant speed regardless of multiplier

        if (lastGraphSpawnAt != 0L && now - lastGraphSpawnAt < spawnIntervalMs) {
            return; // wait for next tick to spawn
        }

        World world = graphBase.getWorld();

        int resetLimit = Math.max(2, width - 2); // reset a bit before full width
        if (graphBlocks.size() >= resetLimit) {
            clearGraphBlocks();
        }

        // Keep Y growth simple and unrelated to crash progress to avoid predictability
        double yOffset = 0.10 + (graphBlocks.size() * 0.06);
        Location loc = graphBase.clone().add(graphBlocks.size() * spacing, yOffset, 0);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.EMERALD_BLOCK));
        display.setTransformation(smallTransform());
        display.setBillboard(ItemDisplay.Billboard.FIXED);
        display.setPersistent(false);
        graphBlocks.add(display);

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
        if (base == null) {
            return null;
        }
        double spacing = config.hologram().getLineSpacing();
        int lineCount = Math.max(0, config.hologram().getLines().size());
        double drop = spacing * (lineCount + 4); // raise the graph closer to the hologram text
        // shift further left to start well left under the text block
        return base.clone().add(-1.5, -drop, 0);
    }

    private void clearGraphBlocks() {
        graphBlocks.forEach(ItemDisplay::remove);
        graphBlocks.clear();
    }

    private void pushHologram(long now) {
        List<String> templates = config.hologram().getLines();
        List<String> rendered = new ArrayList<>(templates.size());
        for (String line : templates) {
            if (state == State.RUNNING && line.contains("{timer}")) {
                continue; // hide next-round timer while game is active
            }
            String out = line
                    .replace("{state}", stateText())
                    .replace("{timer}", String.valueOf(Math.max(0, (nextStartAt - now) / 1000)))
                    .replace("{multiplier}", formatMultiplier(currentMultiplier))
                    .replace("{pot}", formatMoneyShort(potTotal()))
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

    private String renderPlayers() {
        if (bets.isEmpty()) {
            return "None";
        }
        return bets.values().stream()
            .filter(b -> b.getStatus() == Bet.Status.ACTIVE)
            .limit(3)
            .map(b -> b.getPlayerName() + "(" + formatMoneyShort(b.getAmount()) + ChatColor.RESET + ")")
            .collect(Collectors.joining(", "));
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

    private String formatMultiplier(double value) {
        return String.format("%.2f", value);
    }

    private String formatMoney(double value) {
        return config.moneyFormat().format(value);
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
