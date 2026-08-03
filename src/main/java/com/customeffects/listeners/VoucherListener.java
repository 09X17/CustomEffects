package com.customeffects.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.services.DataManager;
import com.customeffects.services.VoucherService;
import com.customeffects.utils.ColorUtils;

public class VoucherListener implements Listener {
    private final CustomEffects plugin;
    private final DataManager dataManager;
    private final VoucherService voucherService;

    public VoucherListener(CustomEffects plugin, DataManager dataManager, VoucherService voucherService) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.voucherService = voucherService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();

            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
                return;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }

            NamespacedKey key = new NamespacedKey(plugin, "voucher_effect_id");
            String effectId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (effectId != null) {
                event.setCancelled(true);

                String permission = findPermissionForEffect(effectId);

                if (permission == null || permission.isEmpty()) {
                    String msg = plugin.getConfig().getString("messages.voucher-corrupt",
                            "%effectos_prefix%&cEste voucher está corrupto o el efecto ya no existe en la configuración.");
                    player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                    return;
                }

                if (player.hasPermission(permission)) {
                    String msg = plugin.getConfig().getString("messages.already-have-permission",
                            "%effectos_prefix%&e¡Ya tienes desbloqueado este efecto!");
                    player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));

                    Location loc = player.getLocation();
                    if (loc != null) {
                        player.playSound(loc, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                    }
                    return;
                }

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }

                String cmd = "lp user " + player.getName() + " permission set " + permission;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

                String effectDisplay = voucherService.getEffectDisplay(effectId);
                String msg = plugin.getConfig().getString("messages.voucher-claimed",
                        "%effectos_prefix%&a&l¡VOUCHER CANJEADO! &7Has desbloqueado el efecto: &e{effect}");
                if (msg == null) {
                    msg = "%effectos_prefix%&a&l¡VOUCHER CANJEADO! &7Has desbloqueado el efecto: &e{effect}";
                }
                msg = msg.replace("{effect}", effectDisplay);
                player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));

                Location loc = player.getLocation();
                if (loc != null) {
                    player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                }
            }
        }
    }

    private String resolvePrefix(String msg) {
        String prefix = plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix);
    }

    private String findPermissionForEffect(String effectId) {
        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect != null) {
                return effect.getPermission();
            }
        }
        return null;
    }
}