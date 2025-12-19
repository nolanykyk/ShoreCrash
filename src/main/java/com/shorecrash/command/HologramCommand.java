package com.shorecrash.command;

import com.shorecrash.CrashPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HologramCommand implements CommandExecutor, TabCompleter {
    private final CrashPlugin plugin;

    public HologramCommand(CrashPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shorecrash.admin")) {
            sender.sendMessage(plugin.getConfigModel().messages().noPermission());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigModel().messages().usageHolo().replace("{label}", label));
            return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getConfigModel().messages().onlyPlayers());
                return true;
            }
            Location loc = player.getLocation();
            plugin.getHologramManager().setLocation(loc);
            plugin.reloadAndBoot();
            sender.sendMessage(plugin.getConfigModel().messages().holoSet());
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            plugin.getHologramManager().clear();
            plugin.getConfig().set("hologram.world", "");
            plugin.getConfig().set("hologram.x", 0.0);
            plugin.getConfig().set("hologram.y", 0.0);
            plugin.getConfig().set("hologram.z", 0.0);
            plugin.getConfig().set("hologram.yaw", 0.0F);
            plugin.saveConfig();
            plugin.reloadAndBoot();
            sender.sendMessage(plugin.getConfigModel().messages().holoCleared());
            return true;
        }
        sender.sendMessage(plugin.getConfigModel().messages().usageHolo().replace("{label}", label));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shorecrash.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(args[0], "set", "clear");
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(String input, String... options) {
        String lower = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return Arrays.stream(options)
                .filter(opt -> opt.startsWith(lower))
                .collect(Collectors.toList());
    }
}
