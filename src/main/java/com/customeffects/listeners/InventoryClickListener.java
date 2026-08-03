package com.customeffects.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.services.DataManager;
import com.customeffects.services.FriendService;
import com.customeffects.services.MenuService;
import com.customeffects.utils.ColorUtils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class InventoryClickListener implements Listener {
    private final CustomEffects plugin;
    private final MenuService menuService;
    private final DataManager dataManager;

    public InventoryClickListener(CustomEffects plugin, MenuService menuService, DataManager dataManager) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.dataManager = dataManager;
    }

    private void playSound(Player player, String configPath, Sound defaultSound) {
        String soundStr = plugin.getConfig().getString(configPath);
        Sound sound = defaultSound;
        try {
            if (soundStr != null && !soundStr.isEmpty())
                sound = Sound.valueOf(soundStr);
        } catch (IllegalArgumentException ignored) {
        }
        Location loc = player.getLocation();
        if (loc != null) {
            player.playSound(loc, sound, 1.0F, 1.0F);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();

        ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta())
            return;

        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());
        
        // Detectar tipo de menú por título
        MenuType menuType = detectMenuType(title);
        
        if (menuType == MenuType.UNKNOWN)
            return;

        event.setCancelled(true);

        switch (menuType) {
            case MAIN_MENU -> handleMainMenuClick(player, item);
            case SUBCATEGORY_MENU -> handleSubcategoryMenuClick(player, item, event.getSlot());
            case EFFECTS_MENU -> handleEffectsMenuClick(player, title, event.getSlot(), item);
            case FRIEND_MENU -> handleFriendMenuClick(player, item, event.getSlot());
        }
    }

    private MenuType detectMenuType(String title) {
        // Menú principal - comparar título exacto
        String mainMenuTitle = dataManager.getMainMenuTitle();
        if (title.equals(mainMenuTitle)) {
            return MenuType.MAIN_MENU;
        }
        
        // Menú de amigos - contiene "Amigos"
        if (title.contains("Amigos")) {
            return MenuType.FRIEND_MENU;
        }
        
        // Menú de efectos - contiene "Pág." o "Pag." O contiene "|" con ":"
        if (title.contains("Pág.") || title.contains("Pag.") || 
            (title.contains("|") && title.contains(":"))) {
            return MenuType.EFFECTS_MENU;
        }
        
        // Menú de subcategorías - contiene "Subcategor" pero NO es menú de efectos
        if (title.contains("Subcategor")) {
            return MenuType.SUBCATEGORY_MENU;
        }
        
        return MenuType.UNKNOWN;
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        NamespacedKey key = new NamespacedKey(plugin, "category_id");
        String catId = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (catId != null) {
            playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK);
            // Friends menu abre directamente el FriendMenuService
            if ("friends_menu".equals(catId)) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getFriendMenuService().openFriendMenu(player));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> menuService.openCategoryMenu(player, catId, 0));
            }
        }
    }

    private void handleSubcategoryMenuClick(Player player, ItemStack item, int slot) {
        // Verificar si es un item de subcategoría
        NamespacedKey subcatKey = new NamespacedKey(plugin, "subcategory_id");
        boolean hasSubcatKey = item.getItemMeta().getPersistentDataContainer().has(subcatKey, PersistentDataType.STRING);
        
        if (hasSubcatKey) {
            String catId = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "category_id"), PersistentDataType.STRING);
            String subcatId = item.getItemMeta().getPersistentDataContainer()
                    .get(subcatKey, PersistentDataType.STRING);
            if (catId != null && subcatId != null) {
                playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK);
                Bukkit.getScheduler().runTask(plugin, () -> menuService.openCategoryMenu(player, catId, subcatId, 0));
            }
            return;
        }
        
        // Verificar si es botón de volver
        ConfigurationSection config = plugin.getConfig();
        int backSlot = config.getInt("navigation.back-main.slot", 45);
        
        if (slot == backSlot) {
            playSound(player, "sounds.back", Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE);
            Bukkit.getScheduler().runTask(plugin, () -> menuService.openMainMenu(player));
        }
    }

    private void handleEffectsMenuClick(Player player, String title, int slot, ItemStack item) {
        ConfigurationSection config = plugin.getConfig();
        String categoryId = extractCategoryFromTitle(title);
        String subcategoryId = extractSubcategoryFromTitle(title);
        int page = extractPageFromTitle(title);

        plugin.getLogger().info("[DEBUG] Title: " + title);
        plugin.getLogger().info("[DEBUG] CategoryId: " + categoryId);
        plugin.getLogger().info("[DEBUG] SubcategoryId: " + subcategoryId);

        // Botón de volver
        if (slot == config.getInt("navigation.back-main.slot", 45)) {
            playSound(player, "sounds.back", Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE);
            if (subcategoryId != null && !subcategoryId.isEmpty()) {
                // Si estamos en una subcategoría, volver al menú de subcategorías (sin subcategoría específica)
                Bukkit.getScheduler().runTask(plugin,
                        () -> menuService.openCategoryMenu(player, categoryId, 0));
            } else {
                // Si estamos en una categoría sin subcategorías, volver al menú principal
                Bukkit.getScheduler().runTask(plugin, () -> menuService.openMainMenu(player));
            }
            return;
        }
        
        // Botón de siguiente página
        if (slot == config.getInt("navigation.next-page.slot", 50)) {
            playSound(player, "sounds.page-turn", Sound.ITEM_BOOK_PAGE_TURN);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    menuService.openCategoryMenu(player, categoryId, subcategoryId, page + 1);
                } else {
                    menuService.openCategoryMenu(player, categoryId, page + 1);
                }
            });
            return;
        }
        
        // Botón de página anterior
        if (slot == config.getInt("navigation.previous-page.slot", 48)) {
            playSound(player, "sounds.page-turn", Sound.ITEM_BOOK_PAGE_TURN);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    menuService.openCategoryMenu(player, categoryId, subcategoryId, page - 1);
                } else {
                    menuService.openCategoryMenu(player, categoryId, page - 1);
                }
            });
            return;
        }
        
        // Botón de resetear efecto
        if (config.getBoolean("reset-item.enable", true) && slot == config.getInt("reset-item.slot", 49)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabase().saveEffect(player.getUniqueId(), "", "");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.translate(
                            resolvePrefix(config.getString("messages.effect-reset-success", "&cEfecto removido."))));
                    if (subcategoryId != null && !subcategoryId.isEmpty()) {
                        menuService.openCategoryMenu(player, categoryId, subcategoryId, page);
                    } else {
                        menuService.openCategoryMenu(player, categoryId, page);
                    }
                });
            });
            return;
        }
        
        // Verificar si es un item de formato
        NamespacedKey formatKey = new NamespacedKey(plugin, "format_style");
        boolean hasFormatKey = item.getItemMeta().getPersistentDataContainer().has(formatKey, PersistentDataType.STRING);
        
        // Verificar si es un efecto
        NamespacedKey effectKey = new NamespacedKey(plugin, "effect_id");
        String effectId = item.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
        
        if (hasFormatKey) {
            handleFormatClick(player, item);
            return;
        }
        
        if (effectId != null) {
            equipEffect(player, effectId, categoryId, subcategoryId, page);
        }
    }

    private void handleFormatClick(Player player, ItemStack item) {
        String style = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "format_style"), PersistentDataType.STRING);
        String category = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "pending_category"), PersistentDataType.STRING);
        String subcategory = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "pending_subcategory"), PersistentDataType.STRING);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabase().saveStyle(player.getUniqueId(), style);
            Bukkit.getScheduler().runTask(plugin, () -> {
                playSound(player, "format-menu.sounds.select", Sound.UI_BUTTON_CLICK);
                if (subcategory != null && !subcategory.isEmpty()) {
                    menuService.openCategoryMenu(player, category, subcategory, 0);
                } else {
                    menuService.openCategoryMenu(player, category, 0);
                }
            });
        });
    }

    private void equipEffect(Player player, String effectId, String categoryId, String subcategoryId, int page) {
        Category category = dataManager.getCategory(categoryId);
        if (category == null) return;

        if (category.isPrefixCategory()) {
            equipPrefix(player, effectId, categoryId, subcategoryId, page);
            return;
        }

        Effect effect = category.getEffectById(effectId);
        if (effect == null || !effect.isEnabled()) return;

        if (effect.hasPermission() && !player.isOp() && !player.hasPermission(effect.getPermission())) return;

        // Efectos normales: guardar effect_id y hex
        String hex = effect.getHex();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabase().saveEffect(player.getUniqueId(), effectId, hex);
            Bukkit.getScheduler().runTask(plugin, () -> {
                playSound(player, "sounds.equip", Sound.ENTITY_PLAYER_LEVELUP);
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    menuService.openCategoryMenu(player, categoryId, subcategoryId, page);
                } else {
                    menuService.openCategoryMenu(player, categoryId, page);
                }
            });
        });
    }

    private void equipPrefix(Player player, String prefixId, String categoryId, String subcategoryId, int page) {
        Category category = dataManager.getCategory(categoryId);
        if (category == null) {
            plugin.getLogger().info("[DEBUG] Category is null for: " + categoryId);
            return;
        }

        Effect effect = category.getEffectById(prefixId);
        if (effect == null) {
            plugin.getLogger().info("[DEBUG] Effect is null for prefixId: " + prefixId);
            return;
        }

        plugin.getLogger().info("[DEBUG] Effect found: " + effect.getId() + " prefix: " + effect.getPrefix());
        plugin.getLogger().info("[DEBUG] Has permission: " + effect.hasPermission() + " player has perm: " + (effect.hasPermission() ? player.hasPermission(effect.getPermission()) : "N/A"));

        if (effect.hasPermission() && !player.isOp() && !player.hasPermission(effect.getPermission()))
            return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String prefix;

            if (prefixId == null || prefixId.isEmpty()) {
                prefix = plugin.getLuckPermsPrefix(player);
                // Desregistrar animación si existe
                plugin.getAnimatedPrefixService().unregisterPlayer(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.translate(resolvePrefix("&a✓ &7Prefix reiniciado al de LuckPerms.")));
                });
            } else {
                // Verificar si el prefix tiene frames animados
                if (effect.hasAnimatedPrefix()) {
                    // Registrar para animación
                    plugin.getAnimatedPrefixService().registerPlayer(player.getUniqueId(), effect);
                    prefix = effect.getPrefixFrame(0);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ColorUtils.translate("&a✓ &7Prefix animado equipado. Velocidad: &f" + effect.getPrefixSpeed() + " ticks"));
                    });
                } else {
                    // Prefix estático normal
                    prefix = effect.getPrefix();
                    // Desregistrar animación si existía
                    plugin.getAnimatedPrefixService().unregisterPlayer(player.getUniqueId());
                }
            }

            plugin.getDatabase().savePrefix(player.getUniqueId(), prefix);

            Bukkit.getScheduler().runTask(plugin, () -> {
                playSound(player, "sounds.equip", Sound.ENTITY_PLAYER_LEVELUP);
                if (subcategoryId != null && !subcategoryId.isEmpty()) {
                    menuService.openCategoryMenu(player, categoryId, subcategoryId, page);
                } else {
                    menuService.openCategoryMenu(player, categoryId, page);
                }
            });
        });
    }

    private String extractCategoryFromTitle(String title) {
        if (title.contains("|")) {
            String[] parts = title.split("\\|");
            if (parts.length > 1) {
                String[] catSub = parts[1].split(":");
                if (catSub.length > 0)
                    return catSub[0].trim();
            }
        }
        for (Category category : dataManager.getAllCategories()) {
            String display = category.getDisplay();
            if (title.contains(display))
                return category.getId();
        }
        return dataManager.getAllCategories().iterator().next().getId();
    }

    private String extractSubcategoryFromTitle(String title) {
        if (title.contains("|")) {
            String[] parts = title.split("\\|");
            if (parts.length > 1) {
                String[] catSub = parts[1].split(":");
                if (catSub.length > 1)
                    return catSub[1].trim();
            }
        }
        return null;
    }

    private int extractPageFromTitle(String title) {
        try {
            if (title.contains("Pág. ")) {
                return Integer.parseInt(title.split("Pág. ")[1].replaceAll("[^0-9]", "")) - 1;
            } else if (title.contains("Pag. ")) {
                return Integer.parseInt(title.split("Pag. ")[1].replaceAll("[^0-9]", "")) - 1;
            }
            return 0;
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    private String resolvePrefix(String msg) {
        return msg.replace("%effectos_prefix%", plugin.getConfig().getString("prefix", ""));
    }

    private enum MenuType {
        UNKNOWN,
        MAIN_MENU,
        SUBCATEGORY_MENU,
        EFFECTS_MENU,
        FRIEND_MENU
    }

    private void handleFriendMenuClick(Player player, ItemStack item, int slot) {
        if (!item.hasItemMeta()) return;

        // Verificar si es un botón
        String buttonKey = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "friend_button"), PersistentDataType.STRING);

        if (buttonKey != null) {
            switch (buttonKey) {
                case "back" -> {
                    playSound(player, "sounds.back", Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE);
                    Bukkit.getScheduler().runTask(plugin, () -> menuService.openMainMenu(player));
                }
                case "add-friend" -> {
                    player.closeInventory();
                    player.sendMessage(ColorUtils.translate("&aEscribe el nombre del jugador: &7/amigo agregar <jugador>"));
                }
                case "requests" -> {
                    FriendService friendService = plugin.getFriendService();
                    var requests = friendService.getPendingRequests(player.getUniqueId());
                    if (requests.isEmpty()) {
                        player.sendMessage(ColorUtils.translate("&cNo tienes solicitudes pendientes."));
                    } else {
                        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                        player.sendMessage(ColorUtils.translate("&a&lSOLICITUDES PENDIENTES"));
                        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                        for (UUID sender : requests) {
                            String name = friendService.getPlayerName(sender);
                            player.sendMessage(ColorUtils.translate(" &7▸ &f" + name + " &7- &a/amigo aceptar " + name + " &7| &c/amigo rechazar " + name));
                        }
                        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    }
                    player.closeInventory();
                }
            }
            return;
        }

        // Verificar si es un amigo (head)
        String friendUuid = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "friend_uuid"), PersistentDataType.STRING);

        if (friendUuid != null) {
            try {
                UUID uuid = UUID.fromString(friendUuid);
                Player friend = Bukkit.getPlayer(uuid);
                if (friend != null) {
                    player.closeInventory();
                    player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    player.sendMessage(ColorUtils.translate("&a&l" + friend.getName()));
                    player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    player.sendMessage(ColorUtils.translate("&7▸ &a/amigo chat " + friend.getName() + " &f- Chat privado"));
                    player.sendMessage(ColorUtils.translate("&7▸ &c/amigo eliminar " + friend.getName() + " &f- Eliminar amigo"));
                    player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                } else {
                    player.sendMessage(ColorUtils.translate("&cEse jugador no está conectado."));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}