package com.customeffects.utils;

import java.util.Base64;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;

public class SkullUtils {

    private SkullUtils() {
    }

    public static void applySkullTexture(ItemStack item, String textureValue) {
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) {
            return;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(textureValue));
            String textureUrl = decoded.replaceAll(".*\"SKIN\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\".*", "$1");

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "CustomSkull");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);

            skullMeta.setOwnerProfile(profile);
            item.setItemMeta(skullMeta);
        } catch (Exception e) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "CustomSkull");
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL("http://textures.minecraft.net/texture/" + textureValue));
                profile.setTextures(textures);

                skullMeta.setOwnerProfile(profile);
                item.setItemMeta(skullMeta);
            } catch (Exception ignored) {
            }
        }
    }
}
