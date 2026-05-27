package com.customeffects;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.utils.ColorUtils;

public class EditorListener implements Listener {

    private final CustomEffects plugin;
    private final Map<Player, String> editingPlayers;
    private final NamespacedKey categoryIdKey;
    private final NamespacedKey effectIdKey;

    public EditorListener(CustomEffects plugin, Map<Player, String> editingPlayers) {
        this.plugin = plugin;
        this.editingPlayers = editingPlayers;
        this.categoryIdKey = new NamespacedKey(plugin, "category_id");
        this.effectIdKey = new NamespacedKey(plugin, "effect_id");
    }

    private boolean isEditorTitle(String title) {
        return isMainMenuEditor(title) || isSubMenuEditor(title);
    }

    private boolean isMainMenuEditor(String title) {
        ConfigurationSection editorConfig = plugin.getConfig().getConfigurationSection("editor");
        String editorTitle = ColorUtils.translate(editorConfig != null ? editorConfig.getString("title", "&8&lEDITOR &7- Menú Principal") : "&8&lEDITOR &7- Menú Principal");
        return title.equals(editorTitle);
    }

    private boolean isSubMenuEditor(String title) {
        return title.contains("EDITOR") && title.contains("Pág.");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!editingPlayers.containsKey(player)) return;

        String title = event.getView().getTitle();
        if (!isEditorTitle(title)) return;

        if (event.getClickedInventory() == null) return;

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        if (isMainMenuEditor(title) && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
                String catId = clicked.getItemMeta().getPersistentDataContainer().get(categoryIdKey, PersistentDataType.STRING);
                if (catId != null) {
                    event.setCancelled(true);
                    editingPlayers.put(player, catId);
                    Bukkit.getScheduler().runTask(plugin, () -> MenuCreator.openCategoryEditor(player, plugin, catId, 0));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!editingPlayers.containsKey(player)) return;
        if (!isEditorTitle(event.getView().getTitle())) return;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!editingPlayers.containsKey(player)) return;

        String title = event.getView().getTitle();
        if (!isEditorTitle(title)) return;

        String editingCategory = editingPlayers.get(player);

        if (isMainMenuEditor(title)) {
            editingPlayers.remove(player);
            saveMainLayout(player, event.getInventory());
        } else {
            saveSubLayout(player, event.getInventory(), editingCategory);
            editingPlayers.put(player, null);
            Bukkit.getScheduler().runTask(plugin, () -> MenuCreator.openEditor(player, plugin));
        }
    }

    private void saveMainLayout(Player player, Inventory inventory) {
        Map<String, Integer> categorySlots = new HashMap<>();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            ItemMeta meta = item.getItemMeta();
            String categoryId = meta.getPersistentDataContainer().get(categoryIdKey, PersistentDataType.STRING);
            if (categoryId != null) {
                categorySlots.put(categoryId, i);
            }
        }

        if (categorySlots.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.editor-closed", "%effectos_prefix%&eEditor cerrado sin cambios.");
            player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
            return;
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        for (Map.Entry<String, Integer> entry : categorySlots.entrySet()) {
            config.set("main-menu.categories." + entry.getKey() + ".slot", entry.getValue());
        }

        try {
            config.save(configFile);
            plugin.reloadConfig();
            plugin.loadEffects();

            String msg = plugin.getConfig().getString("messages.editor-saved", "%effectos_prefix%&aMenú actualizado correctamente. Se guardaron &f{count} &acategorías.");
            msg = msg.replace("{count}", String.valueOf(categorySlots.size()));
            player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
        } catch (Exception e) {
            player.sendMessage(ColorUtils.translate("&cError al guardar: " + e.getMessage()));
        }
    }

    private void saveSubLayout(Player player, Inventory inventory, String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) return;

        Map<String, Integer> effectSlots = new HashMap<>();

        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            ItemMeta meta = item.getItemMeta();
            String effectId = meta.getPersistentDataContainer().get(effectIdKey, PersistentDataType.STRING);
            if (effectId != null) {
                effectSlots.put(effectId, i);
            }
        }

        if (effectSlots.isEmpty()) return;

        File categoryFile = new File(plugin.getDataFolder(), "categories/" + categoryId + ".yml");
        if (!categoryFile.exists()) return;

        YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(categoryFile);

        for (Map.Entry<String, Integer> entry : effectSlots.entrySet()) {
            categoryConfig.set(entry.getKey() + ".slot", entry.getValue());
        }

        try {
            categoryConfig.save(categoryFile);
            plugin.reloadConfig();
            plugin.loadEffects();

            String msg = plugin.getConfig().getString("messages.editor-saved", "%effectos_prefix%&aSub-menú actualizado. Se guardaron &f{count} &aefectos.");
            msg = msg.replace("{count}", String.valueOf(effectSlots.size()));
            player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
        } catch (Exception e) {
            player.sendMessage(ColorUtils.translate("&cError al guardar: " + e.getMessage()));
        }
    }

    private String resolvePrefix(String msg) {
        String prefix = plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix);
    }
}
