package com.shorecrash.command;

import com.shorecrash.CrashPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class ReloadCommand implements CommandExecutor, TabCompleter {
    private final CrashPlugin plugin;

    public ReloadCommand(CrashPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shorecrash.admin")) {
            sender.sendMessage(plugin.getConfigModel().messages().noPermission());
            return true;
        }
        plugin.reloadAndBoot();
        sender.sendMessage(plugin.getConfigModel().messages().reloaded());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.hasPermission("shorecrash.admin") ? Collections.emptyList() : null;
    }
}
