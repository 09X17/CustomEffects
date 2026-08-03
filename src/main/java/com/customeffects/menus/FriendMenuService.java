package com.customeffects.menus;

import com.customeffects.CustomEffects;
import com.customeffects.services.DataManager;
import com.customeffects.services.FriendService;
import com.customeffects.utils.ColorUtils;
import com.customeffects.utils.SkullUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FriendMenuService {
    private final CustomEffects plugin;
    private final DataManager dataManager;
    private final FriendService friendService;

    public FriendMenuService(CustomEffects plugin, DataManager dataManager, FriendService friendService) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.friendService = friendService;
    }

    public void openFriendMenu(Player player) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("friends-menu");
        if (config == null) {
            player.sendMessage(ColorUtils.translate("&cEl menú de amigos no está configurado."));
            return;
        }

        String title = config.getString("title", "{player} &8- Amigos")
                .replace("{player}", player.getName());
        int size = config.getInt("size", 54);

        Component menuTitle = ColorUtils.toComponent(resolvePlaceholders(player, title));
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);

        ConfigurationSection playerHeadConfig = config.getConfigurationSection("player-head");
        if (playerHeadConfig != null) {
            int slot = playerHeadConfig.getInt("slot", 4);
            String display = playerHeadConfig.getString("display", "&a{player}");
            List<String> lore = playerHeadConfig.getStringList("lore");

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullUtils.applyPlayerSkin(head, player);

            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, display)));

                List<Component> finalLore = new ArrayList<>();
                for (String line : lore) {
                    finalLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                }
                meta.lore(finalLore);

                head.setItemMeta(meta);
            }

            if (slot >= 0 && slot < size) {
                inv.setItem(slot, head);
            }
        }

        List<UUID> friends = friendService.getFriends(player.getUniqueId());
        ConfigurationSection friendItemConfig = config.getConfigurationSection("friend-item");

        int friendSlot = 9;
        for (UUID friend : friends) {
            if (friendSlot >= 45) break;

            Player friendPlayer = Bukkit.getPlayer(friend);
            boolean online = friendPlayer != null;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (online) {
                SkullUtils.applyPlayerSkin(head, friendPlayer);
            }

            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                String display = friendItemConfig != null
                        ? friendItemConfig.getString("display", "&f{friend_name}")
                        : "&f{friend_name}";
                display = display.replace("{friend_name}", friendService.getPlayerName(friend));
                meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, display)));

                List<String> loreLines = friendItemConfig != null
                        ? (online ? friendItemConfig.getStringList("lore-online") : friendItemConfig.getStringList("lore-offline"))
                        : new ArrayList<>();

                List<Component> finalLore = new ArrayList<>();
                for (String line : loreLines) {
                    line = line.replace("{friend_name}", friendService.getPlayerName(friend));
                    line = line.replace("{status}", online ? "&aConectado" : "&cDesconectado");

                    if (online) {
                        String effectId = plugin.getDatabase().getActiveEffect(friend);
                        String effectName = effectId != null && !effectId.isEmpty()
                                ? getEffectDisplay(effectId) : "Ninguno";
                        line = line.replace("{effect_name}", effectName);
                    }

                    finalLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                }
                meta.lore(finalLore);

                setPersistentData(meta, "friend_uuid", friend.toString());
                head.setItemMeta(meta);
            }

            inv.setItem(friendSlot, head);
            friendSlot++;
        }

        ConfigurationSection buttonsConfig = config.getConfigurationSection("buttons");
        if (buttonsConfig != null) {
            addButton(inv, buttonsConfig, "back", size);
            addButton(inv, buttonsConfig, "add-friend", size);
            addButton(inv, buttonsConfig, "requests", size);
        }

        addFillerItems(inv, size);
        player.openInventory(inv);
    }

    private void addButton(Inventory inv, ConfigurationSection buttonsConfig, String buttonKey, int size) {
        ConfigurationSection button = buttonsConfig.getConfigurationSection(buttonKey);
        if (button == null) return;

        int slot = button.getInt("slot", 0);
        if (slot < 0 || slot >= size) return;

        Material material = Material.matchMaterial(button.getString("material", "PAPER"));
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.toComponent(button.getString("display", buttonKey)));

            if (button.contains("custom-model-data")) {
                meta.setCustomModelData(button.getInt("custom-model-data"));
            }

            List<String> lore = button.getStringList("lore");
            List<Component> finalLore = new ArrayList<>();
            for (String line : lore) {
                finalLore.add(ColorUtils.toComponent(line));
            }
            meta.lore(finalLore);

            setPersistentData(meta, "friend_button", buttonKey);
            item.setItemMeta(meta);
        }

        inv.setItem(slot, item);
    }

    private void addFillerItems(Inventory inv, int size) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("filler-items");
        if (config == null || !config.getBoolean("enable", false)) return;

        Material fillerMaterial = Material.matchMaterial(config.getString("material", "GRAY_STAINED_GLASS_PANE"));
        if (fillerMaterial == null) fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;

        String display = config.getString("display", " ");

        ItemStack filler = new ItemStack(fillerMaterial);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(ColorUtils.toComponent(display));
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler.clone());
            }
        }
    }

    private void setPersistentData(ItemMeta meta, String key, String value) {
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
    }

    private String resolvePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) return text;
        text = text
                .replace("{player}", player.getName())
                .replace("{friends_count}", String.valueOf(friendService.getFriends(player.getUniqueId()).size()))
                .replace("{friends_online}", String.valueOf(friendService.getOnlineFriends(player.getUniqueId()).size()));
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    private String getEffectDisplay(String effectId) {
        for (var category : dataManager.getAllCategories()) {
            var effect = category.getEffectById(effectId);
            if (effect != null) return effect.getDisplay();
        }
        return effectId;
    }
}