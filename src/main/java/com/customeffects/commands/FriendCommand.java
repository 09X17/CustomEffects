package com.customeffects.commands;

import com.customeffects.CustomEffects;
import com.customeffects.listeners.FriendListener;
import com.customeffects.menus.FriendMenuService;
import com.customeffects.services.FriendService;
import com.customeffects.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class FriendCommand implements CommandExecutor, TabCompleter {
    private final CustomEffects plugin;
    private final FriendService friendService;
    private final FriendMenuService friendMenuService;

    public FriendCommand(CustomEffects plugin, FriendService friendService, FriendMenuService friendMenuService) {
        this.plugin = plugin;
        this.friendService = friendService;
        this.friendMenuService = friendMenuService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.translate("&cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        if (!friendService.isEnabled()) {
            player.sendMessage(ColorUtils.translate("&cEl sistema de amigos está deshabilitado."));
            return true;
        }

        if (args.length == 0) {
            friendMenuService.openFriendMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "agregar", "add" -> handleAdd(player, args);
            case "eliminar", "remove" -> handleRemove(player, args);
            case "aceptar", "accept" -> handleAccept(player, args);
            case "rechazar", "deny" -> handleDeny(player, args);
            case "lista", "list" -> handleList(player);
            case "chat", "msg", "mensaje" -> handleChat(player, args);
            case "menu" -> friendMenuService.openFriendMenu(player);
            default -> sendUsage(player, label);
        }

        return true;
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.translate("&cUso: /amigo agregar <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.translate("&cEl jugador &f" + args[1] + " &cno está conectado."));
            return;
        }

        FriendService.SendResult result = friendService.sendRequest(player.getUniqueId(), target.getUniqueId());
        switch (result) {
            case SUCCESS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.request-sent").replace("{player}", target.getName())));
            case SELF -> player.sendMessage(ColorUtils.translate("&cNo puedes agregarte a ti mismo."));
            case ALREADY_FRIENDS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.already-friend").replace("{player}", target.getName())));
            case ALREADY_SENT -> player.sendMessage(ColorUtils.translate("&cYa enviaste una solicitud a &f" + target.getName() + "&c."));
            case MAX_FRIENDS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.max-friends").replace("{max}", String.valueOf(friendService.getMaxFriends()))));
            case DISABLED -> player.sendMessage(ColorUtils.translate("&cEl sistema de amigos está deshabilitado."));
        }
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.translate("&cUso: /amigo eliminar <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        UUID targetUuid = target != null ? target.getUniqueId() : null;

        if (targetUuid == null) {
            player.sendMessage(ColorUtils.translate("&cEl jugador &f" + args[1] + " &cno está conectado."));
            return;
        }

        FriendService.RemoveResult result = friendService.removeFriend(player.getUniqueId(), targetUuid);
        switch (result) {
            case SUCCESS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.removed").replace("{player}", target.getName())));
            case NOT_FRIENDS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.not-friend").replace("{player}", target.getName())));
        }
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.translate("&cUso: /amigo aceptar <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.translate("&cEl jugador &f" + args[1] + " &cno está conectado."));
            return;
        }

        FriendService.AcceptResult result = friendService.acceptRequest(player.getUniqueId(), target.getUniqueId());
        switch (result) {
            case SUCCESS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.accepted").replace("{player}", target.getName())));
            case NO_REQUEST -> player.sendMessage(ColorUtils.translate("&cNo tienes una solicitud de &f" + target.getName() + "&c."));
            case MAX_FRIENDS -> player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.max-friends").replace("{max}", String.valueOf(friendService.getMaxFriends()))));
        }
    }

    private void handleDeny(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.translate("&cUso: /amigo rechazar <jugador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.translate("&cEl jugador &f" + args[1] + " &cno está conectado."));
            return;
        }

        FriendService.DenyResult result = friendService.denyRequest(player.getUniqueId(), target.getUniqueId());
        switch (result) {
            case SUCCESS -> player.sendMessage(ColorUtils.translate("&aSolicitud de &f" + target.getName() + " &arechazada."));
            case NO_REQUEST -> player.sendMessage(ColorUtils.translate("&cNo tienes una solicitud de &f" + target.getName() + "&c."));
        }
    }

    private void handleList(Player player) {
        List<UUID> friends = friendService.getFriends(player.getUniqueId());
        if (friends.isEmpty()) {
            player.sendMessage(ColorUtils.translate("&cNo tienes amigos agregados."));
            return;
        }

        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(ColorUtils.translate("&a&lTUS AMIGOS &7(" + friends.size() + ")"));
        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        for (UUID friend : friends) {
            String name = friendService.getPlayerName(friend);
            Player friendPlayer = Bukkit.getPlayer(friend);
            String status = friendPlayer != null ? "&a● Conectado" : "&c● Desconectado";
            player.sendMessage(ColorUtils.translate(" &7▸ &f" + name + " " + status));
        }

        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleChat(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("friends.chat.enable", true)) {
            player.sendMessage(ColorUtils.translate("&cEl chat de amigos está deshabilitado."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ColorUtils.translate("&cUso: /amigo chat <jugador> [mensaje]"));
            player.sendMessage(ColorUtils.translate("&7Sin mensaje: activa el modo chat privado con ese amigo."));
            player.sendMessage(ColorUtils.translate("&7Con mensaje: envía un mensaje directo."));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.translate("&cEl jugador &f" + args[1] + " &cno está conectado."));
            return;
        }

        if (!friendService.areFriends(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(ColorUtils.translate(
                    resolveMessage("friends.messages.not-friend").replace("{player}", target.getName())));
            return;
        }

        if (args.length >= 3) {
            // Enviar mensaje directo
            StringBuilder message = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                message.append(args[i]);
                if (i < args.length - 1) message.append(" ");
            }

            String format = plugin.getConfig().getString("friends.chat.format", "&7[&bAmigo&7] &f{sender} &8» &7{message}");
            String prefix = plugin.getConfig().getString("prefix", "");
            String formattedMessage = format
                    .replace("%effectos_prefix%", prefix)
                    .replace("{sender}", player.getName())
                    .replace("{message}", message.toString());

            player.sendMessage(ColorUtils.translate(formattedMessage));
            target.sendMessage(ColorUtils.translate(formattedMessage));
        } else {
            // Activar modo chat privado
            FriendListener friendListener = plugin.getFriendListener();
            friendListener.setPrivateChatTarget(player.getUniqueId(), target.getUniqueId());
            player.sendMessage(ColorUtils.translate("&aModo chat privado activado con &f" + target.getName() + "&a."));
            player.sendMessage(ColorUtils.translate("&7Escribe tu mensaje en el chat normal. Usa &f/amigo chat &7para desactivar."));
        }
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(ColorUtils.translate("&a&lSISTEMA DE AMIGOS"));
        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " &f- Abrir menú de amigos"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " agregar <jugador> &f- Enviar solicitud"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " eliminar <jugador> &f- Eliminar amigo"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " aceptar <jugador> &f- Aceptar solicitud"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " rechazar <jugador> &f- Rechazar solicitud"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " lista &f- Ver amigos"));
        player.sendMessage(ColorUtils.translate("&7▸ /" + label + " chat <jugador> [msg] &f- Chat privado"));
        player.sendMessage(ColorUtils.translate("&a&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private String resolveMessage(String path) {
        String msg = plugin.getConfig().getString(path, "");
        String prefix = plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = List.of("agregar", "eliminar", "aceptar", "rechazar", "lista", "chat", "menu");
            String current = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(current)).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String current = args[1].toLowerCase();
            return switch (sub) {
                case "agregar" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(current))
                        .toList();
                case "eliminar" -> friendService.getFriends(player.getUniqueId()).stream()
                        .map(friendService::getPlayerName)
                        .filter(n -> n.toLowerCase().startsWith(current))
                        .toList();
                case "aceptar", "rechazar" -> friendService.getPendingRequests(player.getUniqueId()).stream()
                        .map(friendService::getPlayerName)
                        .filter(n -> n.toLowerCase().startsWith(current))
                        .toList();
                case "chat" -> friendService.getFriends(player.getUniqueId()).stream()
                        .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                        .map(friendService::getPlayerName)
                        .filter(n -> n.toLowerCase().startsWith(current))
                        .toList();
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }
}