package com.customeffects.services;

import com.customeffects.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class LuckPermsService {
    private final JavaPlugin plugin;

    public LuckPermsService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getPrefix(Player player) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                return "";
            }
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";

            String prefixRaw = user.getCachedData().getMetaData().getPrefix();
            return prefixRaw != null && !prefixRaw.isEmpty() ? ColorUtils.translate(prefixRaw) : "";
        } catch (Exception ignored) {
        }
        return "";
    }

    public String getPlayerGroup(Player player) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                return player.isOp() ? "owner" : "default";
            }
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return player.isOp() ? "owner" : "default";
            String primaryGroup = user.getPrimaryGroup();
            if (primaryGroup == null || primaryGroup.isEmpty()) {
                return player.isOp() ? "owner" : "default";
            }
            return primaryGroup;
        } catch (Exception e) {
            return player.isOp() ? "owner" : "default";
        }
    }

    public boolean canPlayerSeePrefix(Player player, String prefixGroup, List<String> hierarchy) {
        if (prefixGroup == null || prefixGroup.isEmpty() || "all".equals(prefixGroup)) {
            return true;
        }
        String playerGroup = getPlayerGroup(player);
        if (hierarchy.isEmpty()) {
            hierarchy = List.of("default", "nova", "nexus", "void", "mod", "admin", "owner");
        }
        int playerIndex = hierarchy.indexOf(playerGroup);
        int prefixIndex = hierarchy.indexOf(prefixGroup);
        if (playerIndex == -1 || prefixIndex == -1) {
            return playerGroup.equals(prefixGroup);
        }
        return playerIndex >= prefixIndex;
    }
}