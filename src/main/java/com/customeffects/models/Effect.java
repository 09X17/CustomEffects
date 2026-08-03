package com.customeffects.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public class Effect {
    private final String id;
    private final String display;
    private final String hex;
    private final String prefix;
    private final List<String> prefixFrames;
    private final int prefixSpeed;
    private final boolean prefixSmooth;
    private final int prefixTransitionFrames;
    private final String permission;
    private final String preview;
    private final Material material;
    private final int customModelData;
    private final Map<String, Integer> rankModelData;
    private final int slot;
    private final String skullValue;
    private final String group;
    private final boolean enabled;
    private final List<String> lore;
    private final VoucherConfig voucherConfig;

    public Effect(String id, String display, String hex, String prefix, List<String> prefixFrames, int prefixSpeed, 
                  boolean prefixSmooth, int prefixTransitionFrames,
                  String permission, String preview, Material material, int customModelData, 
                  Map<String, Integer> rankModelData, int slot, String skullValue,
                  String group, boolean enabled, List<String> lore, VoucherConfig voucherConfig) {
        this.id = id;
        this.display = display;
        this.hex = hex;
        this.prefix = prefix;
        this.prefixFrames = prefixFrames != null ? prefixFrames : List.of();
        this.prefixSpeed = prefixSpeed;
        this.prefixSmooth = prefixSmooth;
        this.prefixTransitionFrames = prefixTransitionFrames;
        this.permission = permission;
        this.preview = preview;
        this.material = material;
        this.customModelData = customModelData;
        this.rankModelData = rankModelData != null ? rankModelData : new HashMap<>();
        this.slot = slot;
        this.skullValue = skullValue;
        this.group = group;
        this.enabled = enabled;
        this.lore = lore;
        this.voucherConfig = voucherConfig;
    }

    public String getId() { return id; }
    public String getDisplay() { return display; }
    public String getHex() { return hex; }
    public String getPrefix() { return prefix; }
    public List<String> getPrefixFrames() { return prefixFrames; }
    public int getPrefixSpeed() { return prefixSpeed; }
    public boolean isPrefixSmooth() { return prefixSmooth; }
    public int getPrefixTransitionFrames() { return prefixTransitionFrames; }
    public String getPermission() { return permission; }
    public String getPreview() { return preview; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public Map<String, Integer> getRankModelData() { return rankModelData; }
    public int getSlot() { return slot; }
    public String getSkullValue() { return skullValue; }
    public String getGroup() { return group; }
    public boolean isEnabled() { return enabled; }
    public List<String> getLore() { return lore; }
    @Nullable
    public VoucherConfig getVoucherConfig() { return voucherConfig; }

    /**
     * Verifica si este efecto tiene prefix animado (con frames)
     */
    public boolean hasAnimatedPrefix() {
        return prefixFrames != null && !prefixFrames.isEmpty();
    }

    /**
     * Obtiene el frame actual del prefix animado
     * @param frameIndex Índice del frame actual
     * @return El texto del frame actual
     */
    public String getPrefixFrame(int frameIndex) {
        if (prefixFrames == null || prefixFrames.isEmpty()) {
            return prefix;
        }
        int index = frameIndex % prefixFrames.size();
        return prefixFrames.get(index);
    }

    /**
     * Obtiene el custom model data basado en el rango del jugador.
     */
    public int getCustomModelDataForRank(String playerGroup) {
        if (rankModelData.isEmpty()) {
            return customModelData;
        }
        Integer rankCmd = rankModelData.get(playerGroup);
        if (rankCmd != null) {
            return rankCmd;
        }
        Integer defaultCmd = rankModelData.get("default");
        if (defaultCmd != null) {
            return defaultCmd;
        }
        return customModelData;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean isGroupVisible(String playerGroup, List<String> hierarchy) {
        if (group == null || group.isEmpty() || "all".equals(group)) {
            return true;
        }
        return playerGroup.equals(group);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("display", display);
        map.put("hex", hex);
        map.put("permission", permission);
        map.put("preview", preview);
        map.put("material", material.name());
        map.put("custom-model-data", String.valueOf(customModelData));
        map.put("slot", String.valueOf(slot));
        map.put("skull-value", skullValue);
        map.put("group", group);
        map.put("has-custom-lore", lore.isEmpty() ? "false" : "true");
        return map;
    }
}