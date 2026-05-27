package com.customeffects;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.customeffects.utils.ColorUtils;

public class PlayerListener implements Listener {
    private final CustomEffects plugin;

    public PlayerListener(CustomEffects plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.plugin.getDatabase().loadPlayerData(uuid);
            String effectId = this.plugin.getDatabase().getActiveEffect(uuid);
            if (effectId != null && !effectId.isEmpty()) {
                String effectDisplay = this.plugin.getEffectDisplay(effectId);
                String welcomeMsg = this.plugin.getConfig().getString("messages.welcome-back", "%effectos_prefix%&r&aBienvenido de nuevo. Tu efecto activo: &f{effect}")
                        .replace("{effect}", effectDisplay);
                String prefix = this.plugin.getConfig().getString("prefix", "");
                String finalMsg = welcomeMsg.replace("%effectos_prefix%", prefix);
                Bukkit.getScheduler().runTask(this.plugin, () -> player.sendMessage(ColorUtils.translate(finalMsg)));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.plugin.getDatabase().unloadPlayerData(uuid);
    }
}