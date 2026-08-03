package com.customeffects.models;

import java.util.List;

import org.bukkit.Material;

public class Category {
    private final String id;
    private final String display;
    private final String displaySubmenu;
    private final Material material;
    private final int customModelData;
    private final int slot;
    private final String skullValue;
    private final boolean enabled;
    private final boolean prefixCategory;
    private final List<String> lore;
    private final List<Subcategory> subcategories;
    private final List<Effect> effects;

    public Category(String id, String display, String displaySubmenu, Material material,
                    int customModelData, int slot, String skullValue, boolean enabled,
                    boolean prefixCategory, List<String> lore, List<Subcategory> subcategories,
                    List<Effect> effects) {
        this.id = id;
        this.display = display;
        this.displaySubmenu = displaySubmenu;
        this.material = material;
        this.customModelData = customModelData;
        this.slot = slot;
        this.skullValue = skullValue;
        this.enabled = enabled;
        this.prefixCategory = prefixCategory;
        this.lore = lore;
        this.subcategories = subcategories;
        this.effects = effects;
    }

    public String getId() { return id; }
    public String getDisplay() { return display; }
    public String getDisplaySubmenu() { return displaySubmenu; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public int getSlot() { return slot; }
    public String getSkullValue() { return skullValue; }
    public boolean isEnabled() { return enabled; }
    public boolean isPrefixCategory() { return prefixCategory; }
    public List<String> getLore() { return lore; }
    public List<Subcategory> getSubcategories() { return subcategories; }
    public List<Effect> getEffects() { return effects; }

    public boolean hasSubcategories() {
        return !subcategories.isEmpty();
    }

    public Effect getEffectById(String effectId) {
        // Buscar en efectos principales
        Effect effect = effects.stream()
                .filter(e -> e.getId().equals(effectId))
                .findFirst()
                .orElse(null);
        
        if (effect != null) {
            return effect;
        }
        
        // Buscar en subcategorías
        for (Subcategory subcategory : subcategories) {
            effect = subcategory.getEffectById(effectId);
            if (effect != null) {
                return effect;
            }
        }
        
        return null;
    }

    public Subcategory getSubcategoryById(String subcategoryId) {
        return subcategories.stream()
                .filter(s -> s.getId().equals(subcategoryId))
                .findFirst()
                .orElse(null);
    }
}