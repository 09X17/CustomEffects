package com.customeffects.listeners;

import com.customeffects.CustomEffects;
import com.customeffects.services.FriendService;
import com.customeffects.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FriendListener implements Listener {
    private final CustomEffects plugin;
    private final FriendService friendService;
    private final Map<UUID, UUID> privateChatTarget = new HashMap<>();

    public FriendListener(CustomEffects plugin, FriendService friendService) {
        this.plugin = plugin;
        this.friendService = friendService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!friendService.isEnabled()) return;
        if (!plugin.getConfig().getBoolean("friends.notifications.join", true)) return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            List<UUID> friends = friendService.getFriends(player.getUniqueId());
            for (UUID friend : friends) {
                Player friendPlayer = Bukkit.getPlayer(friend);
                if (friendPlayer != null && !friendPlayer.equals(player)) {
                    String msg = plugin.getConfig().getString("friends.messages.online", "&aTu amigo &f{player} &ase ha conectado.");
                    String prefix = plugin.getConfig().getString("prefix", "");
                    msg = msg.replace("%effectos_prefix%", prefix).replace("{player}", player.getName());
                    friendPlayer.sendMessage(ColorUtils.translate(msg));
                }
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!friendService.isEnabled()) return;
        if (!plugin.getConfig().getBoolean("friends.notifications.leave", true)) return;

        List<UUID> friends = friendService.getFriends(player.getUniqueId());
        for (UUID friend : friends) {
            Player friendPlayer = Bukkit.getPlayer(friend);
            if (friendPlayer != null && !friendPlayer.equals(player)) {
                String msg = plugin.getConfig().getString("friends.messages.offline", "&cTu amigo &f{player} &cse ha desconectado.");
                String prefix = plugin.getConfig().getString("prefix", "");
                msg = msg.replace("%effectos_prefix%", prefix).replace("{player}", player.getName());
                friendPlayer.sendMessage(ColorUtils.translate(msg));
            }
        }

        privateChatTarget.remove(player.getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("friends.chat.enable", true)) return;

        Player player = event.getPlayer();
        UUID target = privateChatTarget.get(player.getUniqueId());

        if (target == null) return;

        event.setCancelled(true);

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            privateChatTarget.remove(player.getUniqueId());
            player.sendMessage(ColorUtils.translate("&cTu amigo no está conectado."));
            return;
        }

        String format = plugin.getConfig().getString("friends.chat.format", "&7[&bAmigo&7] &f{sender} &8» &7{message}");
        String prefix = plugin.getConfig().getString("prefix", "");
        String message = format
                .replace("%effectos_prefix%", prefix)
                .replace("{sender}", player.getName())
                .replace("{message}", event.getMessage());

        player.sendMessage(ColorUtils.translate(message));
        targetPlayer.sendMessage(ColorUtils.translate(message));
    }

    public void setPrivateChatTarget(UUID player, UUID target) {
        if (target == null) {
            privateChatTarget.remove(player);
        } else {
            privateChatTarget.put(player, target);
        }
    }

    public UUID getPrivateChatTarget(UUID player) {
        return privateChatTarget.get(player);
    }
}