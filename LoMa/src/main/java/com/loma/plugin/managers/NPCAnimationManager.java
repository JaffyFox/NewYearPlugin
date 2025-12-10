package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import com.loma.plugin.npc.CustomNPC;
import com.loma.plugin.utils.GradientUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Менеджер анимации названий NPC
 */
public class NPCAnimationManager {

    private final LoMa plugin;
    private final Map<Integer, NPCAnimation> animations;
    private BukkitTask animationTask;

    public NPCAnimationManager(LoMa plugin) {
        this.plugin = plugin;
        this.animations = new HashMap<>();
    }

    /**
     * Запускает анимацию для NPC
     */
    public void startAnimation(CustomNPC customNPC, String animatedName) {
        int npcId = customNPC.getId();
        
        // Парсим анимированный текст
        List<String> frames = GradientUtils.parseAnimatedText(animatedName, 20);
        
        if (frames.size() <= 1) {
            // Статичное название
            updateNPCName(npcId, frames.get(0));
            return;
        }

        // Создаём анимацию
        NPCAnimation animation = new NPCAnimation(npcId, frames);
        animations.put(npcId, animation);

        // Запускаем таск анимации если ещё не запущен
        if (animationTask == null || animationTask.isCancelled()) {
            startAnimationTask();
        }
    }

    /**
     * Останавливает анимацию для NPC
     */
    public void stopAnimation(int npcId) {
        animations.remove(npcId);
        
        if (animations.isEmpty() && animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    /**
     * Запускает глобальный таск анимации
     */
    private void startAnimationTask() {
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (NPCAnimation animation : animations.values()) {
                String currentFrame = animation.nextFrame();
                updateNPCName(animation.getNpcId(), currentFrame);
            }
        }, 0L, 5L); // Обновление каждые 5 тиков (0.25 сек)
    }

    /**
     * Обновляет название NPC
     */
    private void updateNPCName(int npcId, String name) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) return;

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null && npc.isSpawned()) {
            npc.setName(name);
        }
    }

    /**
     * Останавливает все анимации
     */
    public void stopAll() {
        animations.clear();
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    /**
     * Перезагружает анимации для всех NPC
     */
    public void reloadAnimations() {
        stopAll();
        
        for (CustomNPC npc : plugin.getNPCManager().getAllNPCs()) {
            String name = npc.getName();
            if (name != null && (name.contains("<gradient:") || name.contains("<bold>"))) {
                startAnimation(npc, name);
            }
        }
    }

    /**
     * Класс анимации для одного NPC
     */
    private static class NPCAnimation {
        private final int npcId;
        private final List<String> frames;
        private int currentFrame;

        public NPCAnimation(int npcId, List<String> frames) {
            this.npcId = npcId;
            this.frames = frames;
            this.currentFrame = 0;
        }

        public int getNpcId() {
            return npcId;
        }

        public String nextFrame() {
            String frame = frames.get(currentFrame);
            currentFrame = (currentFrame + 1) % frames.size();
            return frame;
        }
    }
}
