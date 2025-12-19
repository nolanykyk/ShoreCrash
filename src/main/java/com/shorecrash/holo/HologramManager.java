package com.shorecrash.holo;

import com.shorecrash.config.CrashConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HologramManager {
    private final JavaPlugin plugin;
    private final CrashConfig config;
    private final NamespacedKey holoKey;
    private final NamespacedKey markerKey;
    private List<ArmorStand> hologramLines = new ArrayList<>();
    private final List<ArmorStand> crashMarkers = new ArrayList<>();

    public HologramManager(JavaPlugin plugin, CrashConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.holoKey = new NamespacedKey(plugin, "shorecrash_holo");
        this.markerKey = new NamespacedKey(plugin, "shorecrash_holo_marker");
    }

    public void refresh(List<String> lines) {
        Location base = config.hologram().getLocation();
        if (!config.hologram().isEnabled() || base == null) {
            clear();
            return;
        }
        if (base.getWorld() == null) {
            // World not loaded yet; try again shortly instead of clearing to avoid flicker/desync
            new BukkitRunnable() {
                @Override
                public void run() {
                    refresh(lines);
                }
            }.runTaskLater(plugin, 40L);
            return;
        }
        if (hologramLines.isEmpty()) {
            spawn(base, lines);
        } else {
            updateLines(lines);
        }
    }

    private void spawn(Location base, List<String> lines) {
        clear();
        double spacing = config.hologram().getLineSpacing();
        Location cursor = base.clone();
        World world = base.getWorld();
        List<String> colored = config.hologram().colorizeLines(lines);
        float yaw = (float) base.getYaw();
        for (String line : colored) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(cursor, EntityType.ARMOR_STAND);
            stand.setInvisible(true); // hide model completely
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setRotation(yaw, 0f);
            stand.setCustomNameVisible(true);
            stand.setCustomName(line);
            stand.setSmall(true);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            markPersistent(stand, holoKey);
            hologramLines.add(stand);
            cursor = cursor.clone().add(0, -spacing, 0);
        }
    }

    private void updateLines(List<String> lines) {
        List<String> colored = config.hologram().colorizeLines(lines);
        int shared = Math.min(colored.size(), hologramLines.size());

        for (int i = 0; i < shared; i++) {
            hologramLines.get(i).setCustomName(colored.get(i));
        }

        if (colored.size() > hologramLines.size()) {
            // Add missing lines without respawning everything to avoid flicker
            double spacing = config.hologram().getLineSpacing();
            Location cursor;
            if (hologramLines.isEmpty()) {
                cursor = config.hologram().getLocation();
            } else {
                cursor = hologramLines.get(hologramLines.size() - 1).getLocation().clone().add(0, -spacing, 0);
            }
            World world = cursor.getWorld();
            if (world != null) {
                float yaw = cursor.getYaw();
                for (int i = hologramLines.size(); i < colored.size(); i++) {
                    ArmorStand stand = (ArmorStand) world.spawnEntity(cursor, EntityType.ARMOR_STAND);
                    stand.setInvisible(true);
                    stand.setVisible(false);
                    stand.setMarker(true);
                    stand.setGravity(false);
                    stand.setRotation(yaw, 0f);
                    stand.setCustomNameVisible(true);
                    stand.setCustomName(colored.get(i));
                    stand.setSmall(true);
                    stand.setSilent(true);
                    stand.setInvulnerable(true);
                    stand.setPersistent(true);
                    markPersistent(stand, holoKey);
                    hologramLines.add(stand);
                    cursor = cursor.clone().add(0, -spacing, 0);
                }
            }
        } else if (colored.size() < hologramLines.size()) {
            // Remove surplus lines smoothly
            for (int i = hologramLines.size() - 1; i >= colored.size(); i--) {
                ArmorStand stand = hologramLines.remove(i);
                stand.remove();
            }
        }
    }

    public void clear() {
        if (hologramLines == null) {
            return;
        }
        hologramLines.forEach(ArmorStand::remove);
        hologramLines.clear();
    }

    public void setLocation(Location loc) {
        config.hologram().saveLocation(plugin, loc);
    }

    public void spawnCrashMarker(Location loc, String text, int lifespanSeconds) {
        if (!config.tnt().isEnabled()) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setInvisible(true); // hide base model to prevent visual glitches
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.getEquipment().setHelmet(new ItemStack(Material.TNT));
        stand.setCustomNameVisible(true);
        stand.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
        markPersistent(stand, markerKey);
        crashMarkers.add(stand);
        new BukkitRunnable() {
            @Override
            public void run() {
                stand.remove();
                crashMarkers.remove(stand);
            }
        }.runTaskLater(plugin, lifespanSeconds * 20L);
    }

    public void cleanupOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                PersistentDataContainer data = stand.getPersistentDataContainer();
                if (data.has(holoKey, PersistentDataType.BYTE) || data.has(markerKey, PersistentDataType.BYTE)) {
                    stand.remove();
                }
            }
        }
        hologramLines.clear();
        crashMarkers.clear();
    }

    private void markPersistent(ArmorStand stand, NamespacedKey key) {
        stand.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    public void clearCrashMarkers() {
        crashMarkers.forEach(ArmorStand::remove);
        crashMarkers.clear();
    }

    public List<String> emptyLines(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(" ");
        }
        return lines;
    }
}
