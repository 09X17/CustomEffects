package com.customeffects.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.customeffects.CustomEffects;
import com.customeffects.services.VoucherService;
import com.customeffects.utils.ColorUtils;

public class PlayerListener implements Listener {
    private final CustomEffects plugin;
    private final VoucherService voucherService;

    public PlayerListener(CustomEffects plugin, VoucherService voucherService) {
        this.plugin = plugin;
        this.voucherService = voucherService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabase().loadPlayerData(uuid);
            String effectId = plugin.getDatabase().getActiveEffect(uuid);
            if (effectId != null && !effectId.isEmpty()) {
                String effectDisplay = voucherService.getEffectDisplay(effectId);
                String welcomeMsg = plugin.getConfig().getString("messages.welcome-back",
                        "%effectos_prefix%&r&aBienvenido de nuevo. Tu efecto activo: &f{effect}");
                if (welcomeMsg == null) {
                    welcomeMsg = "%effectos_prefix%&r&aBienvenido de nuevo. Tu efecto activo: &f{effect}";
                }
                welcomeMsg = welcomeMsg.replace("{effect}", effectDisplay);
                String prefix = plugin.getConfig().getString("prefix", "");
                if (prefix == null) {
                    prefix = "";
                }

                String finalMsg = welcomeMsg.replace("%effectos_prefix%", prefix);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ColorUtils.translate(finalMsg)));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getDatabase().unloadPlayerData(uuid);
    }
}