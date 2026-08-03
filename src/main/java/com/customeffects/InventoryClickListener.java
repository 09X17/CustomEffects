package com.customeffects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.utils.ColorUtils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class InventoryClickListener implements Listener {
    private final CustomEffects plugin;

    public InventoryClickListener(CustomEffects plugin) {
        this.plugin = plugin;
    }

    private String stripColors(String text) {
        if (text == null)
            return null;
        return PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private void playSound(Player player, String configPath, Sound defaultSound) {
        String soundStr = this.plugin.getConfig().getString(configPath);
        Sound sound = defaultSound;
        try {
            if (soundStr != null && !soundStr.isEmpty())
                sound = Sound.valueOf(soundStr);
        } catch (IllegalArgumentException ignored) {
        }
        player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !(event.getWhoClicked() instanceof Player))
            return;

        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());
        boolean isMainMenu = title.equals(this.plugin.getMainMenuTitle());
        boolean isSubcategoryMenu = title.contains("Subcategor\u00edas");
        boolean isSubMenu = title.contains("P\u00e1g.") && !isSubcategoryMenu;

        if (!isMainMenu && !isSubMenu && !isSubcategoryMenu)
            return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta())
            return;

        if (isMainMenu) {
            NamespacedKey key = new NamespacedKey(this.plugin, "category_id");
            String catId = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (catId != null) {
                this.playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK);
                Bukkit.getScheduler().runTask(this.plugin,
                        () -> MenuCreator.openCategoryMenu(player, this.plugin, catId, 0));
            }
        } else if (isSubcategoryMenu) {
            NamespacedKey subcatKey = new NamespacedKey(this.plugin, "subcategory_id");
            if (item.getItemMeta().getPersistentDataContainer().has(subcatKey, PersistentDataType.STRING)) {
                String catId = item.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(this.plugin, "category_id"), PersistentDataType.STRING);
                String subcatId = item.getItemMeta().getPersistentDataContainer()
                        .get(subcatKey, PersistentDataType.STRING);
                if (catId != null && subcatId != null) {
                    this.playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK);
                    Bukkit.getScheduler().runTask(this.plugin,
                            () -> MenuCreator.openCategoryMenu(player, this.plugin, catId, subcatId, 0));
                }
            } else {
                NamespacedKey backKey = new NamespacedKey(this.plugin, "back-main");
                int slot = event.getSlot();
                if (slot == this.plugin.getConfig().getInt("navigation.back-main.slot", 45)) {
                    this.playSound(player, "sounds.back", Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE);
                    Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openMainMenu(player, this.plugin));
                }
            }
        } else {
            NamespacedKey formatKey = new NamespacedKey(this.plugin, "format_style");
            if (item.getItemMeta().getPersistentDataContainer().has(formatKey, PersistentDataType.STRING)) {
                handleFormatClick(player, item);
                return;
            }
            handleSubMenuClick(player, title, event.getSlot(), item);
        }
    }

    private void handleFormatClick(Player player, ItemStack item) {
        String style = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(this.plugin, "format_style"), PersistentDataType.STRING);
        String category = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(this.plugin, "pending_category"), PersistentDataType.STRING);
        String subcategory = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(this.plugin, "pending_subcategory"), PersistentDataType.STRING);

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.plugin.getDatabase().saveStyle(player.getUniqueId(), style);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.playSound(player, "format-menu.sounds.select", Sound.UI_BUTTON_CLICK);
                if (subcategory != null && !subcategory.isEmpty()) {
                    MenuCreator.openCategoryMenu(player, this.plugin, category, subcategory, 0);
                } else {
                    MenuCreator.openCategoryMenu(player, this.plugin, category, 0);
                }
            });
        });
    }

    private void handleSubMenuClick(Player player, String title, int slot, ItemStack item) {
        ConfigurationSection config = this.plugin.getConfig();
        String categoryId = extractCategoryFromTitle(title);
        String subcategoryId = extractSubcategoryFromTitle(title);
        int page = extractPageFromTitle(title);
        int effectsPerPage = this.plugin.getEffectsPerPage();

        if (slot == config.getInt("navigation.back-main.slot", 45)) {
            this.playSound(player, "sounds.back", Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE);
            if (subcategoryId != null && !subcategoryId.isEmpty()) {
                Bukkit.getScheduler().runTask(this.plugin,
                        () -> MenuCreator.openCategoryMenu(player, this.plugin, categoryId, subcategoryId, 0));
            } else {
                Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openMainMenu(player, this.plugin));
            }
        } else if (slot == config.getInt("navigation.next-page.slot", 50)) {
            this.playSound(player, "sounds.page-turn", Sound.ITEM_BOOK_PAGE_TURN);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, subcategoryId, page + 1);
                } else {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, page + 1);
                }
            });
        } else if (slot == config.getInt("navigation.previous-page.slot", 48)) {
            this.playSound(player, "sounds.page-turn", Sound.ITEM_BOOK_PAGE_TURN);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, subcategoryId, page - 1);
                } else {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, page - 1);
                }
            });
        } else if (slot == config.getInt("reset-item.slot", 49)) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                this.plugin.getDatabase().saveEffect(player.getUniqueId(), "", "");
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    player.sendMessage(ColorUtils.translate(
                            resolvePrefix(config.getString("messages.effect-reset-success", "&cEfecto removido."))));
                    if (subcategoryId != null && !subcategoryId.isEmpty()) {
                        MenuCreator.openCategoryMenu(player, this.plugin, categoryId, subcategoryId, page);
                    } else {
                        MenuCreator.openCategoryMenu(player, this.plugin, categoryId, page);
                    }
                });
            });
        } else if (slot >= 0 && slot < effectsPerPage) {
            String effectId = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(this.plugin, "effect_id"), PersistentDataType.STRING);
            if (effectId != null)
                equipEffect(player, effectId, categoryId, subcategoryId, page);
        }
    }

    private void equipEffect(Player player, String effectId, String categoryId, String subcategoryId, int page) {
        if (this.plugin.isPrefixCategory(categoryId)) {
            equipPrefix(player, effectId, categoryId, subcategoryId, page);
            return;
        }

        YamlConfiguration catConfig = this.plugin.getCategoryConfig(categoryId);
        String perm = catConfig.getString(effectId + ".permission", "");
        if (!perm.isEmpty() && !player.hasPermission(perm))
            return;

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            String hex = catConfig.getString(effectId + ".hex", "#FFFFFF");
            this.plugin.getDatabase().saveEffect(player.getUniqueId(), effectId, hex);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.playSound(player, "sounds.equip", Sound.ENTITY_PLAYER_LEVELUP);
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, subcategoryId, page);
                } else {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, page);
                }
            });
        });
    }

    private void equipPrefix(Player player, String prefixId, String categoryId, String subcategoryId, int page) {
        YamlConfiguration catConfig = this.plugin.getCategoryConfig(categoryId);
        String perm = catConfig.getString(prefixId + ".permission", "");
        if (!perm.isEmpty() && !player.hasPermission(perm))
            return;

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            String prefix;

            // Si prefixId es vacío, significa que seleccionó "ninguno"
            if (prefixId == null || prefixId.isEmpty()) {
                // Obtener el prefix de LuckPerms
                prefix = this.plugin.getLuckPermsPrefix(player);

                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    player.sendMessage(ColorUtils.translate(
                            resolvePrefix("&a✓ &7Prefix reiniciado al de LuckPerms.")));
                });
            } else {
                // Obtener el prefix del archivo yml
                prefix = this.plugin.getPrefixValue(prefixId, categoryId);
            }

            this.plugin.getDatabase().savePrefix(player.getUniqueId(), prefix);

            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.playSound(player, "sounds.equip", Sound.ENTITY_PLAYER_LEVELUP);
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, subcategoryId, page);
                } else {
                    MenuCreator.openCategoryMenu(player, this.plugin, categoryId, page);
                }
            });
        });
    }

    private String extractCategoryFromTitle(String title) {
        if (title.contains("\u2502")) {
            String[] parts = title.split("\u2502");
            if (parts.length > 1) {
                String[] catSub = parts[1].split(":");
                if (catSub.length > 0)
                    return catSub[0];
            }
        }
        ConfigurationSection categories = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categories == null)
            return "default";
        for (String key : categories.getKeys(false)) {
            String display = stripColors(ColorUtils
                    .translate(this.plugin.getConfig().getString("main-menu.categories." + key + ".display", key)));
            if (title.contains(display))
                return key;
        }
        return categories.getKeys(false).iterator().next();
    }

    private String extractSubcategoryFromTitle(String title) {
        if (title.contains("\u2502")) {
            String[] parts = title.split("\u2502");
            if (parts.length > 1) {
                String[] catSub = parts[1].split(":");
                if (catSub.length > 1)
                    return catSub[1];
            }
        }
        return null;
    }

    private int extractPageFromTitle(String title) {
        try {
            return title.contains("P\u00e1g. ")
                    ? Integer.parseInt(title.split("P\u00e1g. ")[1].replaceAll("[^0-9]", "")) - 1
                    : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String resolvePrefix(String msg) {
        return msg.replace("%effectos_prefix%", this.plugin.getConfig().getString("prefix", ""));
    }
}
