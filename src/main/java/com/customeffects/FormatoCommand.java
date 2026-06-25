package com.customeffects;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FormatoCommand implements CommandExecutor {

    private final CustomEffects plugin;

    public FormatoCommand(CustomEffects plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        if (!player.hasPermission("effectos.style")) {
            player.sendMessage("§cNo tienes permiso para abrir el menú de formatos.");
            return true;
        }

        MenuCreator.openFormatMenu(player, this.plugin, null, null);
        return true;
    }
}
