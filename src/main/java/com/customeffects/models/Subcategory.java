package com.customeffects.models;

import org.bukkit.Material;
import java.util.List;

public class Subcategory {
    private final String id;
    private final String display;
    private final Material material;
    private final int customModelData;
    private final int slot;
    private final String skullValue;
    private final boolean enabled;
    private final List<String> lore;
    private final List<Effect> effects;

    public Subcategory(String id, String display, Material material, int customModelData,
                       int slot, String skullValue, boolean enabled, List<String> lore,
                       List<Effect> effects) {
        this.id = id;
        this.display = display;
        this.material = material;
        this.customModelData = customModelData;
        this.slot = slot;
        this.skullValue = skullValue;
        this.enabled = enabled;
        this.lore = lore;
        this.effects = effects;
    }

    public String getId() { return id; }
    public String getDisplay() { return display; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public int getSlot() { return slot; }
    public String getSkullValue() { return skullValue; }
    public boolean isEnabled() { return enabled; }
    public List<String> getLore() { return lore; }
    public List<Effect> getEffects() { return effects; }

    public Effect getEffectById(String effectId) {
        return effects.stream()
                .filter(e -> e.getId().equals(effectId))
                .findFirst()
                .orElse(null);
    }
}