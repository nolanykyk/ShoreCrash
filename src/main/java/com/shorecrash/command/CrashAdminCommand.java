package com.shorecrash.command;

import com.shorecrash.game.CrashGame;
import com.shorecrash.util.AmountParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CrashAdminCommand implements CommandExecutor, TabCompleter {
    private final CrashGame game;

    public CrashAdminCommand(CrashGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shorecrash.admin")) {
            sender.sendMessage(game.getConfigModel().messages().noPermission());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " rig <multiplier>|summary");
            return true;
        }

        if (args[0].equalsIgnoreCase("summary")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(game.getConfigModel().messages().onlyPlayers());
                return true;
            }
            game.openServerSummaryGui(player);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(game.getConfigModel().messages().onlyPlayers());
            return true;
        }

        if (!args[0].equalsIgnoreCase("rig") || args.length < 2) {
            sender.sendMessage("Usage: /" + label + " rig <multiplier>|summary");
            return true;
        }

        Double value = AmountParser.parse(args[1]);
        if (value == null || value <= 0) {
            sender.sendMessage(game.getConfigModel().messages().invalidAmount().replace("{input}", args[1]));
            return true;
        }
        game.tryRigNextCrash(player, value);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shorecrash.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(args[0], "rig", "summary");
        }
        if (args.length == 2 && "rig".equalsIgnoreCase(args[0])) {
            return filterPrefix(args[1], "2.0", "5.0", "10.0");
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(String input, String... options) {
        String lower = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return Arrays.stream(options)
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}