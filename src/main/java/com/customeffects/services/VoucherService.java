package com.customeffects.services;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.models.VoucherConfig;
import com.customeffects.utils.ColorUtils;

import net.kyori.adventure.text.Component;

public class VoucherService {
    private final CustomEffects plugin;
    private final DataManager dataManager;

    public VoucherService(CustomEffects plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @Nullable
    public ItemStack createVoucher(String effectId) {
        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect == null)
                continue;
            if (!effect.isEnabled())
                return null;

            VoucherConfig vc = effect.getVoucherConfig();
            if (vc == null)
                return null;

            String effectName = effect.getDisplay().contains("›")
                    ? effect.getDisplay().substring(effect.getDisplay().indexOf("›") + 2)
                    : effect.getDisplay();

            String resolvedDisplay = vc.getDisplayName()
                    .replace("{hex}", effect.getHex())
                    .replace("{name}", effectName);

            ItemStack item = new ItemStack(vc.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta == null)
                return null;

            meta.displayName(ColorUtils.toComponent(resolvedDisplay));

            List<Component> lore = new ArrayList<>();
            for (String line : vc.getLore()) {
                lore.add(ColorUtils.toComponent(
                        line.replace("{hex}", effect.getHex()).replace("{name}", effectName)));
            }
            meta.lore(lore);

            if (vc.getCustomModelData() != 0) {
                meta.setCustomModelData(vc.getCustomModelData());
            }

            NamespacedKey key = new NamespacedKey(plugin, "voucher_effect_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, effectId);

            item.setItemMeta(meta);
            return item;
        }

        return null;
    }

    public boolean giveVoucherToPlayer(Player target, String effectId) {
        ItemStack voucher = createVoucher(effectId);
        if (voucher == null)
            return false;

        if (target.getInventory().firstEmpty() == -1) {
            Location loc = target.getLocation();
            if (loc != null) {
                target.getWorld().dropItemNaturally(loc, voucher);
            }
        } else {
            target.getInventory().addItem(voucher);
        }
        return true;
    }

    public String getEffectDisplay(String effectId) {
        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect != null) {
                return effect.getDisplay();
            }
        }
        return effectId;
    }
}