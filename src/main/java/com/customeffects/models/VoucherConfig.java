package com.customeffects.models;

import org.bukkit.Material;
import java.util.List;

public class VoucherConfig {
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final int customModelData;

    public VoucherConfig(Material material, String displayName, List<String> lore, int customModelData) {
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.customModelData = customModelData;
    }

    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public int getCustomModelData() { return customModelData; }
}