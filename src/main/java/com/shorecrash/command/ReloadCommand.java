package com.shorecrash.command;

import com.shorecrash.CrashPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
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
}
