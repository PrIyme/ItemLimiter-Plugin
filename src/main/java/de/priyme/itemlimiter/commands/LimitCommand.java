package de.priyme.itemlimiter.commands;

import de.priyme.itemlimiter.gui.LimitGUI;
import de.priyme.itemlimiter.manager.LimitManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LimitCommand implements CommandExecutor {

    private final LimitManager limitManager;

    public LimitCommand(LimitManager limitManager) {
        this.limitManager = limitManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only for players.");
            return true;
        }

        if (!player.hasPermission("itemlimiter.admin")) {
            player.sendMessage("§cYou do not have permission.");
            return true;
        }

        new LimitGUI(limitManager, player).open();
        return true;
    }
}
