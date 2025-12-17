package com.shorecrash.command;

import com.shorecrash.game.CrashGame;
import com.shorecrash.util.AmountParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CrashAdminCommand implements CommandExecutor {
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(game.getConfigModel().messages().onlyPlayers());
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("rig")) {
            sender.sendMessage(game.getConfigModel().messages().usageCrash().replace("{label}", label));
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
}