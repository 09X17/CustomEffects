package com.customeffects.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Base64;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;

public class SkullUtils {

    private SkullUtils() {
    }

    @SuppressWarnings("deprecation")
    public static void applySkullTexture(ItemStack item, String textureValue) {
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) {
            return;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(textureValue));
            String textureUrl = decoded.replaceAll(
                ".*\"SKIN\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\".*", "$1"
            );

            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(textureUrl).toURL()); 
            profile.setTextures(textures);

            skullMeta.setOwnerProfile(profile);
            item.setItemMeta(skullMeta);

        } catch (MalformedURLException | IllegalArgumentException e) { 
            try {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(URI.create(
                    "http://textures.minecraft.net/texture/" + textureValue
                ).toURL());
                profile.setTextures(textures);

                skullMeta.setOwnerProfile(profile);
                item.setItemMeta(skullMeta);

            } catch (MalformedURLException | IllegalArgumentException ignored) { 
            }
        }
    }

    /**
     * Aplica la skin del jugador a un ItemStack de tipo PLAYER_HEAD
     * @param item ItemStack de tipo PLAYER_HEAD
     * @param player Jugador cuya skin se aplicará
     */
    @SuppressWarnings("deprecation")
    public static void applyPlayerSkin(ItemStack item, Player player) {
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) {
            return;
        }

        try {
            // Obtener el perfil del jugador
            PlayerProfile profile = player.getPlayerProfile();
            
            // Verificar si el perfil tiene texturas
            if (profile.getTextures().getSkin() == null) {
                // Si no tiene texturas, completar el perfil
                profile = Bukkit.createProfile(player.getUniqueId(), player.getName());
                profile.complete();
            }
            
            skullMeta.setOwnerProfile(profile);
            item.setItemMeta(skullMeta);
        } catch (Exception e) {
            // Fallback: usar el nombre del jugador
            skullMeta.setOwner(player.getName());
            item.setItemMeta(skullMeta);
        }
    }
}