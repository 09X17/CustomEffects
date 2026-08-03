package com.customeffects.listeners;

import com.customeffects.CustomEffects;
import com.customeffects.utils.ColorUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.event.user.track.UserTrackEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuckPermsRankChangeListener {
    private final CustomEffects plugin;
    private EventSubscription<UserTrackEvent> trackSubscription;
    private EventSubscription<UserDataRecalculateEvent> recalculateSubscription;
    private EventSubscription<NodeAddEvent> nodeAddSubscription;
    private EventSubscription<NodeRemoveEvent> nodeRemoveSubscription;

    private final Map<UUID, String> lastKnownPrefix = new ConcurrentHashMap<>();

    public LuckPermsRankChangeListener(CustomEffects plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                plugin.getLogger().warning("LuckPerms no encontrado. Listener de cambio de rango no registrado.");
                return;
            }

            LuckPerms luckPerms = LuckPermsProvider.get();
            EventBus eventBus = luckPerms.getEventBus();

            this.trackSubscription = eventBus.subscribe(plugin, UserTrackEvent.class, this::onRankChange);
            this.recalculateSubscription = eventBus.subscribe(plugin, UserDataRecalculateEvent.class, this::onUserDataRecalculate);
            this.nodeAddSubscription = eventBus.subscribe(plugin, NodeAddEvent.class, this::onNodeAdd);
            this.nodeRemoveSubscription = eventBus.subscribe(plugin, NodeRemoveEvent.class, this::onNodeRemove);

            plugin.getLogger().info("Listener de cambio de rango LuckPerms registrado (API completa).");
        } catch (Exception e) {
            plugin.getLogger().warning("Error al registrar listener de LuckPerms: " + e.getMessage());
        }
    }

    public void unregister() {
        if (this.trackSubscription != null) this.trackSubscription.close();
        if (this.recalculateSubscription != null) this.recalculateSubscription.close();
        if (this.nodeAddSubscription != null) this.nodeAddSubscription.close();
        if (this.nodeRemoveSubscription != null) this.nodeRemoveSubscription.close();
        lastKnownPrefix.clear();
    }

    private void onRankChange(UserTrackEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        String newGroup = event.getGroupTo().orElse(null);

        if (newGroup == null) return;

        plugin.getAnimatedPrefixService().unregisterPlayer(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String currentPrefix = plugin.getDatabase().getPrefix(uuid);

            if (currentPrefix != null && !currentPrefix.isEmpty()) {
                plugin.getDatabase().savePrefix(uuid, "");
            }

            lastKnownPrefix.remove(uuid);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.translate("&a✓ &7Tu rango ha cambiado. Tu prefix ha sido reiniciado."));
                });
            }
        });
    }

    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;

            String newLuckPermsPrefix = plugin.getLuckPermsPrefix(player);
            String oldPrefix = lastKnownPrefix.get(uuid);

            if (oldPrefix != null && oldPrefix.equals(newLuckPermsPrefix)) return;

            lastKnownPrefix.put(uuid, newLuckPermsPrefix);

            String customPrefix = plugin.getDatabase().getPrefix(uuid);
            if (customPrefix == null || customPrefix.isEmpty()) return;

            List<String> hierarchy = plugin.getConfig().getStringList("prefix-system.rank-hierarchy");
            String playerGroup = plugin.getPlayerGroup(player);

            boolean prefixBelongsToVisibleEffect = isPrefixValidForRank(customPrefix, playerGroup, hierarchy);
            boolean prefixIsLuckPermsDefault = customPrefix.equals(newLuckPermsPrefix);

            if (!prefixBelongsToVisibleEffect && !prefixIsLuckPermsDefault) {
                plugin.getAnimatedPrefixService().unregisterPlayer(uuid);
                plugin.getDatabase().savePrefix(uuid, "");
            }
        });
    }

    private void onNodeAdd(NodeAddEvent event) {
        if (!event.isUser()) return;
        if (!event.getNode().getType().equals(NodeType.PREFIX)) return;

        UUID uuid = ((User) event.getTarget()).getUniqueId();

        plugin.getAnimatedPrefixService().unregisterPlayer(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String customPrefix = plugin.getDatabase().getPrefix(uuid);
            if (customPrefix == null || customPrefix.isEmpty()) return;

            plugin.getDatabase().savePrefix(uuid, "");

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.translate("&a✓ &7Tu prefix de LuckPerms ha cambiado. Tu prefix personalizado ha sido reiniciado."));
                });
            }
        });
    }

    private void onNodeRemove(NodeRemoveEvent event) {
        if (!event.isUser()) return;
        if (!event.getNode().getType().equals(NodeType.PREFIX)) return;

        UUID uuid = ((User) event.getTarget()).getUniqueId();

        plugin.getAnimatedPrefixService().unregisterPlayer(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String customPrefix = plugin.getDatabase().getPrefix(uuid);
            if (customPrefix == null || customPrefix.isEmpty()) return;

            plugin.getDatabase().savePrefix(uuid, "");

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.translate("&a✓ &7Tu prefix de LuckPerms ha cambiado. Tu prefix personalizado ha sido reiniciado."));
                });
            }
        });
    }

    private boolean isPrefixValidForRank(String prefix, String playerGroup, List<String> hierarchy) {
        var categories = plugin.getDataManager().getAllCategories();
        for (var category : categories) {
            if (!category.isPrefixCategory()) continue;
            for (var effect : category.getEffects()) {
                if (prefix.equals(effect.getPrefix())) {
                    return effect.isGroupVisible(playerGroup, hierarchy);
                }
            }
        }
        return false;
    }
}
