package com.customeffects.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Base64;
import java.util.UUID;

import org.bukkit.Bukkit;
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
}