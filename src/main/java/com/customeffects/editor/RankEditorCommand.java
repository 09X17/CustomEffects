package com.customeffects.editor;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.customeffects.CustomEffects;
import com.customeffects.utils.ColorUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public class RankEditorCommand implements CommandExecutor, TabCompleter {

    private final CustomEffects plugin;

    public RankEditorCommand(CustomEffects plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        if (!player.hasPermission("effectos.rankeditor")) {
            player.sendMessage(ColorUtils.translate(
                    plugin.getConfig().getString("prefix", "") +
                    "&cNo tienes permiso para usar el editor de rangos."));
            return true;
        }

        WebEditorServer editorServer = plugin.getWebEditorServer();
        if (editorServer == null || !editorServer.isRunning()) {
            player.sendMessage(ColorUtils.translate(
                    plugin.getConfig().getString("prefix", "") +
                    "&cEl editor de rangos no esta habilitado o no se pudo iniciar."));
            player.sendMessage(ColorUtils.translate(
                    "&7Revisa los logs del servidor para mas informacion."));
            return true;
        }

        String token = editorServer.generateToken();
        int port = editorServer.getPort();

        String publicHost = plugin.getConfig().getString("rank-editor.public-host", "");
        if (publicHost == null) publicHost = "";
        publicHost = publicHost.trim();

        if (publicHost.isEmpty()) {
            try {
                publicHost = plugin.getServer().getIp();
            } catch (Exception ignored) {}
            if (publicHost == null || publicHost.isEmpty() || "0.0.0.0".equals(publicHost)) {
                player.sendMessage(ColorUtils.translate(
                        plugin.getConfig().getString("prefix", "") +
                        "&cNo se pudo detectar la IP del servidor."));
                player.sendMessage(ColorUtils.translate(
                        "&7Configura &fpublic-host &7en plugins/CustomEffects/config.yml con la IP de tu VPS."));
                return true;
            }
        }

        String protocol = editorServer.isHttps() ? "https" : "http";
        String url = protocol + "://" + publicHost + ":" + port + "?token=" + token;

        Component prefix = ColorUtils.toComponent(
                plugin.getConfig().getString("prefix", ""));

        Component message = Component.text()
                .append(prefix)
                .append(Component.text("Editor de Rangos: ", NamedTextColor.GRAY))
                .append(Component.text("[Click para abrir]", NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(
                                Component.text(url, NamedTextColor.YELLOW))))
                .build();

        player.sendMessage(message);

        player.sendMessage(Component.text("  URL: " + url, NamedTextColor.DARK_GRAY));

        plugin.getLogger().info(player.getName() + " abrio el editor: " + url);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
