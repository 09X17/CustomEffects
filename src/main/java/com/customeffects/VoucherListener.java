package com.customeffects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.utils.ColorUtils;

public class VoucherListener implements Listener {

    private final CustomEffects plugin;

    public VoucherListener(CustomEffects plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();

            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
                return;
            }

            NamespacedKey key = new NamespacedKey(plugin, "voucher_effect_id");
            String effectId = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (effectId != null) {
                event.setCancelled(true);

                String permission = findPermissionForEffect(effectId);

                if (permission == null || permission.isEmpty()) {
                    String msg = plugin.getConfig().getString("messages.voucher-corrupt", "%effectos_prefix%&cEste voucher está corrupto o el efecto ya no existe en la configuración.");
                    player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                    return;
                }

                if (player.hasPermission(permission)) {
                    String msg = plugin.getConfig().getString("messages.already-have-permission", "%effectos_prefix%&e¡Ya tienes desbloqueado este efecto!");
                    player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                    return;
                }

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }

                String cmd = "lp user " + player.getName() + " permission set " + permission;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

                String effectDisplay = plugin.getEffectDisplay(effectId);
                String msg = plugin.getConfig().getString("messages.voucher-claimed", "%effectos_prefix%&a&l¡VOUCHER CANJEADO! &7Has desbloqueado el efecto: &e{effect}");
                msg = msg.replace("{effect}", effectDisplay);
                player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
        }
    }

    private String resolvePrefix(String msg) {
        String prefix = plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix);
    }

    private String findPermissionForEffect(String effectId) {
        java.util.Set<String> categories = plugin.getConfig().getConfigurationSection("main-menu.categories").getKeys(false);
        for (String categoryKey : categories) {
            YamlConfiguration categoryConfig = plugin.getCategoryConfig(categoryKey);
            if (categoryConfig != null && categoryConfig.contains(effectId)) {
                return categoryConfig.getString(effectId + ".permission", "");
            }
        }
        return null;
    }
}
