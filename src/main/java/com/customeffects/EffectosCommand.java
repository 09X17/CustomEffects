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

import net.kyori.adventure.text.Component;

import com.customeffects.utils.ColorUtils;

public class EffectosCommand implements CommandExecutor, TabCompleter {
    private final CustomEffects plugin;

    public EffectosCommand(CustomEffects plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("effectos.reload")) {
                String msg = this.plugin.getConfig().getString("messages.reload-no-permission", "%effectos_prefix%&cNo tienes permiso para recargar la configuración.");
                sender.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                return true;
            }
            this.plugin.reloadConfig();
            this.plugin.loadEffects();
            String msg = this.plugin.getConfig().getString("messages.reload-success", "%effectos_prefix%&aConfiguración y efectos recargados con éxito.");
            sender.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("givevoucher")) {
            if (!sender.hasPermission("effectos.admin")) {
                String msg = this.plugin.getConfig().getString("messages.voucher-no-permission", "%effectos_prefix%&cNo tienes permiso para dar vouchers.");
                sender.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                return true;
            }

            if (args.length < 3) {
                String msg = this.plugin.getConfig().getString("messages.voucher-usage", "%effectos_prefix%&c§lUso Incorrecto › &7/{cmd} givevoucher <jugador> <id_efecto>");
                msg = msg.replace("{cmd}", label);
                sender.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                String msg = this.plugin.getConfig().getString("messages.player-not-found", "%effectos_prefix%&cEl jugador '&f{player}&c' no se encuentra conectado.");
                msg = msg.replace("{player}", args[1]);
                sender.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                return true;
            }

            String effectId = args[2];
            ItemStack voucher = this.createVoucher(effectId);

            if (voucher == null) {
                String msg = this.plugin.getConfig().getString("messages.voucher-not-found", "%effectos_prefix%&cNo se encontró ningún voucher configurado para el efecto: &e{effect}");
                msg = msg.replace("{effect}", effectId);
                sender.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                return true;
            }

            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItemNaturally(target.getLocation(), voucher);
            } else {
                target.getInventory().addItem(voucher);
            }

            String senderMsg = this.plugin.getConfig().getString("messages.voucher-given-sender", "%effectos_prefix%&aHas entregado 1x Voucher de &e{effect} &aa &f{player}&a.");
            senderMsg = senderMsg.replace("{effect}", effectId).replace("{player}", target.getName());
            sender.sendMessage(ColorUtils.translate(resolvePrefix(senderMsg)));

            String targetMsg = this.plugin.getConfig().getString("messages.voucher-given-target", "%effectos_prefix%&a¡Has recibido un voucher para el efecto: &e{effect}&a!");
            targetMsg = targetMsg.replace("{effect}", effectId);
            target.sendMessage(ColorUtils.translate(resolvePrefix(targetMsg)));
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("effectos.menu")) {
                player.sendMessage("§cNo tienes permiso para abrir el menú de efectos.");
                return true;
            }
            MenuCreator.openMainMenu(player, this.plugin);
            return true;
        } else {
            sender.sendMessage("§c[Effectos] Comandos disponibles desde consola:");
            sender.sendMessage("§7▸ /" + label + " reload");
            sender.sendMessage("§7▸ /" + label + " givevoucher <jugador> <id_efecto>");
            return true;
        }
    }

    private String resolvePrefix(String msg) {
        String prefix = this.plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix);
    }

    private @Nullable ItemStack createVoucher(String effectId) {
        ConfigurationSection categoriesSection = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection == null) return null;

        for (String categoryKey : categoriesSection.getKeys(false)) {
            YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(categoryKey);
            if (categoryConfig != null && categoryConfig.contains(effectId)) {
                if (!categoryConfig.getBoolean(effectId + ".enable", true)) {
                    return null;
                }

                String effectHex = categoryConfig.getString(effectId + ".hex", "#FFFFFF");
                String effectDisplay = categoryConfig.getString(effectId + ".display", effectId);
                String effectName = effectDisplay.contains("›") ? effectDisplay.substring(effectDisplay.indexOf("›") + 2) : effectDisplay;

                String path = effectId + ".voucher";
                boolean hasPerEffectVoucher = categoryConfig.contains(path);

                String matPath = hasPerEffectVoucher ? path + ".material" : null;
                String displayPath = hasPerEffectVoucher ? path + ".displayname" : null;
                String lorePath = hasPerEffectVoucher ? path + ".lore" : null;
                String cmdPath = hasPerEffectVoucher ? path + ".custom-model-data" : null;

                Material mat;
                String rawDisplay;
                List<String> rawLore;
                int customModelData;
                boolean hasCustomModel;

                if (hasPerEffectVoucher) {
                    mat = Material.valueOf(categoryConfig.getString(matPath, "PAPER"));
                    rawDisplay = categoryConfig.getString(displayPath, "&d&lVoucher");
                    rawLore = categoryConfig.getStringList(lorePath);
                    hasCustomModel = categoryConfig.contains(cmdPath);
                    customModelData = hasCustomModel ? categoryConfig.getInt(cmdPath) : 0;
                } else {
                    ConfigurationSection globalVoucher = this.plugin.getConfig().getConfigurationSection("global-voucher");
                    if (globalVoucher == null) return null;

                    mat = Material.valueOf(globalVoucher.getString("material", "PAPER"));
                    rawDisplay = globalVoucher.getString("displayname", "&d&lVoucher");
                    rawLore = globalVoucher.getStringList("lore");
                    hasCustomModel = globalVoucher.contains("custom-model-data");
                    customModelData = hasCustomModel ? globalVoucher.getInt("custom-model-data") : 0;
                }

                String resolvedDisplay = rawDisplay.replace("{hex}", effectHex).replace("{name}", effectName);

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    meta.displayName(ColorUtils.toComponent(resolvedDisplay));

                    List<Component> translatedLore = new ArrayList<>();
                    for (String line : rawLore) {
                        String resolvedLine = line.replace("{hex}", effectHex).replace("{name}", effectName);
                        translatedLore.add(ColorUtils.toComponent(resolvedLine));
                    }
                    meta.lore(translatedLore);

                    if (hasCustomModel) {
                        meta.setCustomModelData(customModelData);
                    }

                    NamespacedKey key = new NamespacedKey(this.plugin, "voucher_effect_id");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, effectId);

                    item.setItemMeta(meta);
                    return item;
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            if (sender.hasPermission("effectos.reload") && "reload".startsWith(currentArg)) {
                suggestions.add("reload");
            }
            if (sender.hasPermission("effectos.admin") && "givevoucher".startsWith(currentArg)) {
                suggestions.add("givevoucher");
            }
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givevoucher")) {
            String currentArg = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(currentArg)) {
                    suggestions.add(p.getName());
                }
            }
            return suggestions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givevoucher")) {
            String currentArg = args[2].toLowerCase();
            ConfigurationSection categoriesSection = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
            if (categoriesSection != null) {
                for (String categoryKey : categoriesSection.getKeys(false)) {
                    YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(categoryKey);
                    if (categoryConfig != null) {
                        for (String effectKey : categoryConfig.getKeys(false)) {
                            if (categoryConfig.isConfigurationSection(effectKey) && effectKey.toLowerCase().startsWith(currentArg) && categoryConfig.getBoolean(effectKey + ".enable", true)) {
                                suggestions.add(effectKey);
                            }
                        }
                    }
                }
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}