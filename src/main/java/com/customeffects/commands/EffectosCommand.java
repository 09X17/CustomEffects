package com.customeffects.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.customeffects.CustomEffects;
import com.customeffects.services.MenuService;
import com.customeffects.services.VoucherService;
import com.customeffects.utils.ColorUtils;

public class EffectosCommand implements CommandExecutor, TabCompleter {
    private final CustomEffects plugin;
    private final MenuService menuService;
    private final VoucherService voucherService;

    public EffectosCommand(CustomEffects plugin, MenuService menuService, VoucherService voucherService) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.voucherService = voucherService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1) {
            String sub = args[0].toLowerCase();

            if (sub.equals("reload")) {
                return handleReload(sender);
            }

            if (sub.equals("givevoucher")) {
                return handleGiveVoucher(sender, label, args);
            }
        }

        if (sender instanceof Player player) {
            if (!player.hasPermission("effectos.menu")) {
                player.sendMessage(ColorUtils.translate("&cNo tienes permiso para abrir el menú de efectos."));
                return true;
            }
            menuService.openMainMenu(player);
        } else {
            sender.sendMessage(ColorUtils.translate("&c[Effectos] Comandos disponibles desde consola:"));
            sender.sendMessage(ColorUtils.translate("&7▸ /" + label + " reload"));
            sender.sendMessage(ColorUtils.translate("&7▸ /" + label + " givevoucher <jugador> <id_efecto>"));
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("effectos.reload")) {
            sender.sendMessage(ColorUtils.translate(resolveMessage("messages.reload-no-permission",
                    "%effectos_prefix%&cNo tienes permiso para recargar la configuración.")));
            return true;
        }

        plugin.reloadConfig();
        plugin.loadEffects();
        sender.sendMessage(ColorUtils.translate(resolveMessage("messages.reload-success",
                "%effectos_prefix%&aConfiguración y efectos recargados con éxito.")));
        return true;
    }

    private boolean handleGiveVoucher(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("effectos.admin")) {
            sender.sendMessage(ColorUtils.translate(resolveMessage("messages.voucher-no-permission",
                    "%effectos_prefix%&cNo tienes permiso para dar vouchers.")));
            return true;
        }

        if (args.length < 3) {
            String msg = resolveMessage("messages.voucher-usage",
                    "%effectos_prefix%&c§lUso Incorrecto › &7/{cmd} givevoucher <jugador> <id_efecto>");
            sender.sendMessage(ColorUtils.translate(msg.replace("{cmd}", label)));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            String msg = resolveMessage("messages.player-not-found",
                    "%effectos_prefix%&cEl jugador '&f{player}&c' no se encuentra conectado.");
            sender.sendMessage(ColorUtils.translate(msg.replace("{player}", args[1])));
            return true;
        }

        String effectId = args[2];
        String effectDisplay = voucherService.getEffectDisplay(effectId);
        boolean success = voucherService.giveVoucherToPlayer(target, effectId);

        if (!success) {
            String msg = resolveMessage("messages.voucher-not-found",
                    "%effectos_prefix%&cNo se encontró ningún voucher configurado para el efecto: &e{effect}");
            sender.sendMessage(ColorUtils.translate(msg.replace("{effect}", effectDisplay)));
            return true;
        }

        String senderMsg = resolveMessage("messages.voucher-given-sender",
                "%effectos_prefix%&aHas entregado 1x Voucher de &e{effect} &aa &f{player}&a.")
                .replace("{effect}", effectDisplay)
                .replace("{player}", target.getName());
        sender.sendMessage(ColorUtils.translate(senderMsg));

        String targetMsg = resolveMessage("messages.voucher-given-target",
                "%effectos_prefix%&a¡Has recibido un voucher para el efecto: &e{effect}&a!")
                .replace("{effect}", effectDisplay);
        target.sendMessage(ColorUtils.translate(targetMsg));

        return true;
    }

    private String resolveMessage(String path, String defaultValue) {
        String raw = Objects.requireNonNullElse(
                plugin.getConfig().getString(path, defaultValue), "");
        String prefix = Objects.requireNonNullElse(
                plugin.getConfig().getString("prefix", ""), "");
        return raw.replace("%effectos_prefix%", prefix);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String current = args[0].toLowerCase();
            if (sender.hasPermission("effectos.reload") && "reload".startsWith(current))
                suggestions.add("reload");
            if (sender.hasPermission("effectos.admin") && "givevoucher".startsWith(current))
                suggestions.add("givevoucher");
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givevoucher")) {
            String current = args[1].toLowerCase();
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(current))
                    players.add(p.getName());
            }
            return players;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givevoucher")) {
            String current = args[2].toLowerCase();
            List<String> effects = new ArrayList<>();
            plugin.getDataManager().getAllCategories().forEach(category -> {
                category.getEffects().forEach(effect -> {
                    if (effect.getId().toLowerCase().startsWith(current)) {
                        effects.add(effect.getId());
                    }
                });
            });
            return effects;
        }

        return Collections.emptyList();
    }
}