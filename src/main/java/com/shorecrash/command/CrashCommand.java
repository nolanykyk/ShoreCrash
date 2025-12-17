package com.shorecrash.command;

import com.shorecrash.game.CrashGame;
import com.shorecrash.util.AmountParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CrashCommand implements CommandExecutor {
    private final CrashGame game;

    public CrashCommand(CrashGame game) {
        this.game = game;
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

        Double amount = AmountParser.parse(args[0]);
        if (amount == null || amount <= 0) {
            player.sendMessage(game.getConfigModel().messages().invalidAmount().replace("{input}", args[0]));
            return true;
        }
        game.placeBet(player, amount);
        return true;
    }
}
