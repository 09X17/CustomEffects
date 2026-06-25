package com.customeffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.customeffects.utils.ColorUtils;

import net.kyori.adventure.text.Component;

public class EffectosCommand implements CommandExecutor, TabCompleter {

    private final CustomEffects plugin;

    public EffectosCommand(CustomEffects plugin) {
        this.plugin = plugin;
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
                player.sendMessage("§cNo tienes permiso para abrir el menú de efectos.");
                return true;
            }
            MenuCreator.openMainMenu(player, this.plugin);
        } else {
            sender.sendMessage("§c[Effectos] Comandos disponibles desde consola:");
            sender.sendMessage("§7▸ /" + label + " reload");
            sender.sendMessage("§7▸ /" + label + " givevoucher <jugador> <id_efecto>");
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("effectos.reload")) {
            sender.sendMessage(ColorUtils.translate(resolveMessage("messages.reload-no-permission",
                    "%effectos_prefix%&cNo tienes permiso para recargar la configuración.")));
            return true;
        }

        this.plugin.reloadConfig();
        this.plugin.loadEffects();
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
        String effectDisplay = this.plugin.getEffectDisplay(effectId);
        ItemStack voucher = createVoucher(effectId);

        if (voucher == null) {
            String msg = resolveMessage("messages.voucher-not-found",
                    "%effectos_prefix%&cNo se encontró ningún voucher configurado para el efecto: &e{effect}");
            sender.sendMessage(ColorUtils.translate(msg.replace("{effect}", effectDisplay)));
            return true;
        }

        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItemNaturally(target.getLocation(), voucher);
        } else {
            target.getInventory().addItem(voucher);
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

    private @Nullable ItemStack createVoucher(String effectId) {
        ConfigurationSection categoriesSection =
                this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection == null) return null;

        for (String categoryKey : categoriesSection.getKeys(false)) {
            YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(categoryKey);
            if (categoryConfig == null || !categoryConfig.contains(effectId)) continue;
            if (!categoryConfig.getBoolean(effectId + ".enable", true)) return null;

            String effectHex  = categoryConfig.getString(effectId + ".hex", "#FFFFFF");
            String effectDisplay = categoryConfig.getString(effectId + ".display", effectId);
            String effectName = effectDisplay.contains("›")
                    ? effectDisplay.substring(effectDisplay.indexOf("›") + 2)
                    : effectDisplay;

            VoucherConfig vc = readVoucherConfig(categoryConfig, effectId);
            if (vc == null) return null;

            String resolvedDisplay = vc.displayName
                    .replace("{hex}", effectHex)
                    .replace("{name}", effectName);

            ItemStack item = new ItemStack(vc.material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;

            meta.displayName(ColorUtils.toComponent(resolvedDisplay));

            List<Component> lore = new ArrayList<>();
            for (String line : vc.lore) {
                lore.add(ColorUtils.toComponent(
                        line.replace("{hex}", effectHex).replace("{name}", effectName)));
            }
            meta.lore(lore);

            if (vc.customModelData != 0) {
                meta.setCustomModelData(vc.customModelData);
            }

            NamespacedKey key = new NamespacedKey(this.plugin, "voucher_effect_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, effectId);

            item.setItemMeta(meta);
            return item;
        }

        return null;
    }

    private @Nullable VoucherConfig readVoucherConfig(YamlConfiguration categoryConfig, String effectId) {
        String path = effectId + ".voucher";

        if (categoryConfig.contains(path)) {
            return new VoucherConfig(
                    Material.valueOf(categoryConfig.getString(path + ".material", "PAPER")),
                    categoryConfig.getString(path + ".displayname", "&d&lVoucher"),
                    categoryConfig.getStringList(path + ".lore"),
                    categoryConfig.getInt(path + ".custom-model-data", 0)
            );
        }

        ConfigurationSection global = this.plugin.getConfig().getConfigurationSection("global-voucher");
        if (global == null) return null;

        return new VoucherConfig(
                Material.valueOf(global.getString("material", "PAPER")),
                global.getString("displayname", "&d&lVoucher"),
                global.getStringList("lore"),
                global.getInt("custom-model-data", 0)
        );
    }

    private record VoucherConfig(
            Material material,
            String displayName,
            List<String> lore,
            int customModelData
    ) {}

    private String resolveMessage(String path, String defaultValue) {
        String raw = this.plugin.getConfig().getString(path, defaultValue);
        String prefix = this.plugin.getConfig().getString("prefix", "");
        return raw.replace("%effectos_prefix%", prefix);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String current = args[0].toLowerCase();
            if (sender.hasPermission("effectos.reload")  && "reload".startsWith(current))      suggestions.add("reload");
            if (sender.hasPermission("effectos.admin")   && "givevoucher".startsWith(current)) suggestions.add("givevoucher");
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givevoucher")) {
            String current = args[1].toLowerCase();
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(current)) players.add(p.getName());
            }
            return players;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givevoucher")) {
            String current = args[2].toLowerCase();
            List<String> effects = new ArrayList<>();
            ConfigurationSection categoriesSection =
                    this.plugin.getConfig().getConfigurationSection("main-menu.categories");
            if (categoriesSection != null) {
                for (String categoryKey : categoriesSection.getKeys(false)) {
                    YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(categoryKey);
                    if (categoryConfig == null) continue;
                    for (String effectKey : categoryConfig.getKeys(false)) {
                        if (categoryConfig.isConfigurationSection(effectKey)
                                && effectKey.toLowerCase().startsWith(current)
                                && categoryConfig.getBoolean(effectKey + ".enable", true)) {
                            effects.add(effectKey);
                        }
                    }
                }
            }
            return effects;
        }

        return Collections.emptyList();
    }
}