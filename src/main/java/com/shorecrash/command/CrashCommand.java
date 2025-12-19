package com.shorecrash.command;

import com.shorecrash.game.CrashGame;
import com.shorecrash.stats.StatsService;
import com.shorecrash.util.AmountParser;
import org.bukkit.Bukkit;
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

public class CrashCommand implements CommandExecutor, TabCompleter {
    private final CrashGame game;
    private final StatsService stats;

    public CrashCommand(CrashGame game, StatsService stats) {
        this.game = game;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(game.getConfigModel().messages().onlyPlayers());
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(game.getConfigModel().messages().usageCrash().replace("{label}", label));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("cashout")) {
            game.cashout(player);
            return true;
        }
        if (sub.equals("cancel")) {
            game.cancelBet(player);
            return true;
        }
        if (sub.equals("stats")) {
            if (!player.hasPermission("shorecrash.stats")) {
                player.sendMessage(game.getConfigModel().messages().noPermission());
                return true;
            }
            String targetName = args.length >= 2 ? args[1] : player.getName();
            game.openPlayerStatsGui(player, targetName);
            return true;
        }
        if (sub.equals("lastgames")) {
            game.sendLastGames(player);
            return true;
        }

        Double amount = AmountParser.parse(args[0]);
        if (amount == null || amount <= 0) {
            player.sendMessage(game.getConfigModel().messages().invalidAmount().replace("{input}", args[0]));
            return true;
        }
        game.placeBet(player, amount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(args[0], "cashout", "cancel", "stats", "lastgames");
        }
        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
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
