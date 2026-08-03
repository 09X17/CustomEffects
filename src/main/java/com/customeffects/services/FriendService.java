package com.customeffects.services;

import com.customeffects.CustomEffects;
import com.customeffects.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FriendService {
    private final CustomEffects plugin;
    private final DataManager dataManager;

    public FriendService(CustomEffects plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("friends.enable", true);
    }

    public int getMaxFriends() {
        return plugin.getConfig().getInt("friends.max-friends", 50);
    }

    public SendResult sendRequest(UUID sender, UUID receiver) {
        if (!isEnabled()) return SendResult.DISABLED;
        if (sender.equals(receiver)) return SendResult.SELF;
        if (plugin.getDatabase().areFriends(sender, receiver)) return SendResult.ALREADY_FRIENDS;
        if (plugin.getDatabase().hasPendingRequest(sender, receiver)) return SendResult.ALREADY_SENT;
        if (plugin.getDatabase().getFriends(sender).size() >= getMaxFriends()) return SendResult.MAX_FRIENDS;

        plugin.getDatabase().sendRequest(sender, receiver);

        Player receiverPlayer = Bukkit.getPlayer(receiver);
        if (receiverPlayer != null) {
            String msg = resolveMessage("friends.messages.request-received", "{player}", getPlayerName(sender));
            receiverPlayer.sendMessage(ColorUtils.translate(msg));
        }

        return SendResult.SUCCESS;
    }

    public AcceptResult acceptRequest(UUID receiver, UUID sender) {
        if (!plugin.getDatabase().hasPendingRequest(sender, receiver)) return AcceptResult.NO_REQUEST;
        if (plugin.getDatabase().getFriends(receiver).size() >= getMaxFriends()) return AcceptResult.MAX_FRIENDS;

        plugin.getDatabase().removeRequest(sender, receiver);
        plugin.getDatabase().addFriend(receiver, sender);

        Player senderPlayer = Bukkit.getPlayer(sender);
        if (senderPlayer != null) {
            String msg = resolveMessage("friends.messages.friend-accepted", "{player}", getPlayerName(receiver));
            senderPlayer.sendMessage(ColorUtils.translate(msg));
        }

        return AcceptResult.SUCCESS;
    }

    public DenyResult denyRequest(UUID receiver, UUID sender) {
        if (!plugin.getDatabase().hasPendingRequest(sender, receiver)) return DenyResult.NO_REQUEST;

        plugin.getDatabase().removeRequest(sender, receiver);
        return DenyResult.SUCCESS;
    }

    public RemoveResult removeFriend(UUID player, UUID friend) {
        if (!plugin.getDatabase().areFriends(player, friend)) return RemoveResult.NOT_FRIENDS;

        plugin.getDatabase().removeFriend(player, friend);

        Player friendPlayer = Bukkit.getPlayer(friend);
        if (friendPlayer != null) {
            String msg = resolveMessage("friends.messages.friend-removed", "{player}", getPlayerName(player));
            friendPlayer.sendMessage(ColorUtils.translate(msg));
        }

        return RemoveResult.SUCCESS;
    }

    public List<UUID> getFriends(UUID uuid) {
        return plugin.getDatabase().getFriends(uuid);
    }

    public List<UUID> getOnlineFriends(UUID uuid) {
        List<UUID> online = new ArrayList<>();
        for (UUID friend : getFriends(uuid)) {
            if (Bukkit.getPlayer(friend) != null) {
                online.add(friend);
            }
        }
        return online;
    }

    public List<UUID> getPendingRequests(UUID uuid) {
        return plugin.getDatabase().getPendingRequests(uuid);
    }

    public boolean areFriends(UUID player, UUID friend) {
        return plugin.getDatabase().areFriends(player, friend);
    }

    public String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player.getName();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private String resolveMessage(String path, String placeholder, String value) {
        String msg = plugin.getConfig().getString(path, "");
        String prefix = plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix).replace(placeholder, value);
    }

    public enum SendResult { SUCCESS, DISABLED, SELF, ALREADY_FRIENDS, ALREADY_SENT, MAX_FRIENDS }
    public enum AcceptResult { SUCCESS, NO_REQUEST, MAX_FRIENDS }
    public enum DenyResult { SUCCESS, NO_REQUEST }
    public enum RemoveResult { SUCCESS, NOT_FRIENDS }
}