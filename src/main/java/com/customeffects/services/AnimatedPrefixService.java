package com.customeffects.services;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnimatedPrefixService {
    private final CustomEffects plugin;
    private final DataManager dataManager;
    private final Map<UUID, AnimationState> animatedPlayers = new ConcurrentHashMap<>();
    private BukkitTask animationTask;
    private static final Pattern HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");

    public AnimatedPrefixService(CustomEffects plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void start() {
        animationTask = new BukkitRunnable() {
            private int globalTick = 0;

            @Override
            public void run() {
                globalTick++;
                
                for (Map.Entry<UUID, AnimationState> entry : animatedPlayers.entrySet()) {
                    UUID uuid = entry.getKey();
                    AnimationState state = entry.getValue();
                    
                    if (globalTick % state.speed == 0) {
                        state.currentFrame++;
                        
                        String currentPrefix = state.getCurrentFrameText();
                        plugin.getDatabase().savePrefix(uuid, currentPrefix);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        animatedPlayers.clear();
    }

    public void registerPlayer(UUID uuid, Effect effect) {
        if (effect == null || !effect.hasAnimatedPrefix()) {
            return;
        }

        int speed = effect.getPrefixSpeed();
        if (speed <= 0) speed = 10;

        List<String> expandedFrames;
        if (effect.isPrefixSmooth()) {
            expandedFrames = generateSmoothFrames(effect.getPrefixFrames(), effect.getPrefixTransitionFrames());
        } else {
            expandedFrames = effect.getPrefixFrames();
        }

        AnimationState state = new AnimationState(effect, expandedFrames, 0, speed);
        animatedPlayers.put(uuid, state);

        String firstFrame = expandedFrames.get(0);
        plugin.getDatabase().savePrefix(uuid, firstFrame);
    }

    public void unregisterPlayer(UUID uuid) {
        animatedPlayers.remove(uuid);
    }

    public boolean isAnimated(UUID uuid) {
        return animatedPlayers.containsKey(uuid);
    }

    public int getCurrentFrame(UUID uuid) {
        AnimationState state = animatedPlayers.get(uuid);
        return state != null ? state.currentFrame : 0;
    }

    public Effect getAnimatedEffect(UUID uuid) {
        AnimationState state = animatedPlayers.get(uuid);
        return state != null ? state.effect : null;
    }

    public Effect findAnimatedPrefixEffect(String effectId) {
        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect != null && effect.hasAnimatedPrefix()) {
                return effect;
            }
        }
        return null;
    }

    /**
     * Genera frames intermedios para transiciones suaves
     * Interpola los colores hex entre los frames principales
     */
    private List<String> generateSmoothFrames(List<String> originalFrames, int transitionFrames) {
        if (originalFrames.size() < 2) return new ArrayList<>(originalFrames);

        List<String> smoothFrames = new ArrayList<>();

        for (int i = 0; i < originalFrames.size(); i++) {
            smoothFrames.add(originalFrames.get(i));

            if (i < originalFrames.size() - 1) {
                String current = originalFrames.get(i);
                String next = originalFrames.get(i + 1);
                List<String> transitions = interpolateFrames(current, next, transitionFrames);
                smoothFrames.addAll(transitions);
            } else {
                // Transición del último frame al primero (loop)
                String current = originalFrames.get(i);
                String next = originalFrames.get(0);
                List<String> transitions = interpolateFrames(current, next, transitionFrames);
                smoothFrames.addAll(transitions);
            }
        }

        return smoothFrames;
    }

    /**
     * Genera frames intermedios interpolando los colores entre dos frames
     */
    private List<String> interpolateFrames(String frameA, String frameB, int count) {
        List<String> transitions = new ArrayList<>();

        String hexA = extractHex(frameA);
        String hexB = extractHex(frameB);

        if (hexA == null || hexB == null) {
            // Si no hay hex, simplemente repetimos el frame
            for (int i = 0; i < count; i++) {
                transitions.add(frameA);
            }
            return transitions;
        }

        int[] rgbA = hexToRgb(hexA);
        int[] rgbB = hexToRgb(hexB);

        for (int i = 1; i <= count; i++) {
            double ratio = (double) i / (count + 1);
            int r = (int) (rgbA[0] + (rgbB[0] - rgbA[0]) * ratio);
            int g = (int) (rgbA[1] + (rgbB[1] - rgbA[1]) * ratio);
            int b = (int) (rgbA[2] + (rgbB[2] - rgbA[2]) * ratio);

            String interpolatedHex = String.format("#%02x%02x%02x", r, g, b);
            String interpolatedFrame = replaceHex(frameA, interpolatedHex);
            transitions.add(interpolatedFrame);
        }

        return transitions;
    }

    /**
     * Extrae el código hex de un texto con formato
     */
    private String extractHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Convierte un código hex a RGB
     */
    private int[] hexToRgb(String hex) {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new int[]{r, g, b};
    }

    /**
     * Reemplaza el código hex en un texto de formato
     */
    private String replaceHex(String text, String newHex) {
        return HEX_PATTERN.matcher(text).replaceAll(newHex);
    }

    private static class AnimationState {
        final Effect effect;
        final List<String> expandedFrames;
        int currentFrame;
        final int speed;

        AnimationState(Effect effect, List<String> expandedFrames, int currentFrame, int speed) {
            this.effect = effect;
            this.expandedFrames = expandedFrames;
            this.currentFrame = currentFrame;
            this.speed = speed;
        }

        String getCurrentFrameText() {
            if (expandedFrames.isEmpty()) {
                return effect.getPrefix();
            }
            int index = currentFrame % expandedFrames.size();
            return expandedFrames.get(index);
        }
    }
}