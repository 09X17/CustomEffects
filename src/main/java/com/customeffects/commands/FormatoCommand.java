package com.customeffects.commands;

import com.customeffects.CustomEffects;
import com.customeffects.services.MenuService;
import com.customeffects.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FormatoCommand implements CommandExecutor {
    private final CustomEffects plugin;
    private final MenuService menuService;

    public FormatoCommand(CustomEffects plugin, MenuService menuService) {
        this.plugin = plugin;
        this.menuService = menuService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.translate("&cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        if (!player.hasPermission("effectos.formato")) {
            player.sendMessage(ColorUtils.translate("&cNo tienes permiso para usar este comando."));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ColorUtils.translate("&cUso: /formato <categoría> <efecto>"));
            return true;
        }

        String categoryId = args[0];
        String effectId = args[1];

        if (!plugin.getDataManager().isValidCategory(categoryId)) {
            player.sendMessage(ColorUtils.translate("&cLa categoría '&f" + categoryId + "&c' no existe."));
            return true;
        }

        menuService.openFormatMenu(player, effectId, categoryId);
        return true;
    }
}