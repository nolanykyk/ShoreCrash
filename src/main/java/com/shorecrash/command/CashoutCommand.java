package com.shorecrash.command;

import com.shorecrash.game.CrashGame;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CashoutCommand implements CommandExecutor {
    private final CrashGame game;

    public CashoutCommand(CrashGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(game.getConfigModel().messages().onlyPlayers());
            return true;
        }
        game.cashout(player);
        return true;
    }
}
