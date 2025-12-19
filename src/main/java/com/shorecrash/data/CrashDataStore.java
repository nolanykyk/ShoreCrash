package com.shorecrash.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class CrashDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Deque<Double> crashHistory = new ArrayDeque<>();
    private int maxSize = 20;

    public CrashDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "crashdata.yml");
        load();
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
        trim();
    }

    public void record(double value) {
        crashHistory.addLast(value);
        trim();
        save();
    }

    public List<Double> recent(int count) {
        int take = Math.min(count, crashHistory.size());
        List<Double> out = new ArrayList<>(take);
        int skip = crashHistory.size() - take;
        int idx = 0;
        for (Double d : crashHistory) {
            if (idx++ < skip) continue;
            out.add(d);
        }
        return out;
    }

    private void trim() {
        while (crashHistory.size() > maxSize) {
            crashHistory.pollFirst();
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("crashes", new ArrayList<>(crashHistory));
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save crashdata.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Double> list = cfg.getDoubleList("crashes");
        crashHistory.clear();
        crashHistory.addAll(list);
        trim();
    }
}
