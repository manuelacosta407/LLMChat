package com.sodium.llmchat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CooldownManager {

    private final LLMChatPlugin plugin;
    private final Set<String> whitelist;
    private volatile int defaultMaxRequests;
    private volatile int defaultCooldownSeconds;
    private final Map<String, ModelCooldownConfig> modelConfigs;
    private final Map<String, ModelUsage> modelUsage;

    public CooldownManager(LLMChatPlugin plugin) {
        this.plugin = plugin;
        this.whitelist = ConcurrentHashMap.newKeySet();
        this.modelConfigs = new ConcurrentHashMap<>();
        this.modelUsage = new ConcurrentHashMap<>();
        this.defaultMaxRequests = 5;
        this.defaultCooldownSeconds = 60;
        loadConfig();
    }

    public void loadConfig() {
        whitelist.clear();
        modelConfigs.clear();

        ConfigurationSection cooldownSection = plugin.getConfig().getConfigurationSection("cooldown");
        if (cooldownSection == null) {
            plugin.getLogger().warning("No cooldown section found in config.yml, using defaults");
            return;
        }

        List<String> whitelistList = cooldownSection.getStringList("whitelist");
        for (String name : whitelistList) {
            whitelist.add(name.toLowerCase());
        }

        ConfigurationSection defaultSection = cooldownSection.getConfigurationSection("default");
        if (defaultSection != null) {
            defaultMaxRequests = defaultSection.getInt("max_requests_per_minute", 5);
            defaultCooldownSeconds = defaultSection.getInt("cooldown_seconds", 60);
        }

        ConfigurationSection modelsSection = cooldownSection.getConfigurationSection("models");
        if (modelsSection != null) {
            for (String modelName : modelsSection.getKeys(false)) {
                ConfigurationSection modelSection = modelsSection.getConfigurationSection(modelName);
                if (modelSection != null) {
                    int maxRequests = modelSection.getInt("max_requests_per_minute", defaultMaxRequests);
                    int cooldownSeconds = modelSection.getInt("cooldown_seconds", defaultCooldownSeconds);
                    modelConfigs.put(modelName.toLowerCase(), new ModelCooldownConfig(maxRequests, cooldownSeconds));
                }
            }
        }

        plugin.getLogger().info("Loaded cooldown config: whitelist=" + whitelist.size() + 
                ", default_max=" + defaultMaxRequests + ", models=" + modelConfigs.size());
    }

    public boolean isWhitelisted(String playerName) {
        return playerName != null && whitelist.contains(playerName.toLowerCase());
    }

    public ModelCooldownConfig getModelConfig(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return new ModelCooldownConfig(defaultMaxRequests, defaultCooldownSeconds);
        }
        return modelConfigs.getOrDefault(modelName.toLowerCase(), 
                new ModelCooldownConfig(defaultMaxRequests, defaultCooldownSeconds));
    }

    public boolean canUse(String playerName, String modelName) {
        if (isWhitelisted(playerName)) {
            return true;
        }

        String key = modelName != null ? modelName.toLowerCase() : "_default_";
        ModelUsage usage = modelUsage.get(key);
        
        if (usage == null) {
            return true;
        }

        if (usage.isInCooldown()) {
            return false;
        }

        ModelCooldownConfig config = getModelConfig(modelName);
        return usage.getRequestCount() < config.maxRequestsPerMinute;
    }

    public int getRemainingCooldownSeconds(String modelName) {
        String key = modelName != null ? modelName.toLowerCase() : "_default_";
        ModelUsage usage = modelUsage.get(key);
        
        if (usage == null || !usage.isInCooldown()) {
            return 0;
        }

        return usage.getRemainingCooldownSeconds();
    }

    public void recordUse(String modelName) {
        String key = modelName != null ? modelName.toLowerCase() : "_default_";
        ModelCooldownConfig config = getModelConfig(modelName);
        
        modelUsage.computeIfAbsent(key, k -> new ModelUsage(config.cooldownSeconds, config.maxRequestsPerMinute))
                .recordRequest();
    }

    public void clearAllUsage() {
        modelUsage.clear();
    }

    public static class ModelCooldownConfig {
        public final int maxRequestsPerMinute;
        public final int cooldownSeconds;

        ModelCooldownConfig(int maxRequestsPerMinute, int cooldownSeconds) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
            this.cooldownSeconds = cooldownSeconds;
        }
    }

    private static class ModelUsage {
        private final ConcurrentLinkedQueue<Long> requestTimestamps;
        private final AtomicInteger cooldownStartTime;
        private final int cooldownSeconds;
        private final int maxRequestsPerMinute;
        private final AtomicLong lastCleanupTime;
        private static final long CLEANUP_INTERVAL_MS = 10000;

        ModelUsage(int cooldownSeconds, int maxRequestsPerMinute) {
            this.requestTimestamps = new ConcurrentLinkedQueue<>();
            this.cooldownSeconds = cooldownSeconds;
            this.maxRequestsPerMinute = maxRequestsPerMinute;
            this.cooldownStartTime = new AtomicInteger(0);
            this.lastCleanupTime = new AtomicLong(0);
        }

        void recordRequest() {
            long now = System.currentTimeMillis();
            
            if (now - lastCleanupTime.get() > CLEANUP_INTERVAL_MS) {
                cleanupOldTimestamps(now);
                lastCleanupTime.set(now);
            }
            
            requestTimestamps.add(now);

            if (requestTimestamps.size() >= maxRequestsPerMinute) {
                cooldownStartTime.set((int) (now / 1000));
            }
        }

        private void cleanupOldTimestamps(long now) {
            long oneMinuteAgo = now - 60000;
            requestTimestamps.removeIf(timestamp -> timestamp < oneMinuteAgo);
        }

        boolean isInCooldown() {
            int startTime = cooldownStartTime.get();
            if (startTime == 0) {
                return false;
            }
            long elapsed = (System.currentTimeMillis() / 1000) - startTime;
            return elapsed < cooldownSeconds;
        }

        int getRemainingCooldownSeconds() {
            int startTime = cooldownStartTime.get();
            if (startTime == 0) {
                return 0;
            }
            long elapsed = (System.currentTimeMillis() / 1000) - startTime;
            int remaining = cooldownSeconds - (int) elapsed;
            return Math.max(0, remaining);
        }

        int getRequestCount() {
            long now = System.currentTimeMillis();
            if (now - lastCleanupTime.get() > CLEANUP_INTERVAL_MS) {
                cleanupOldTimestamps(now);
                lastCleanupTime.set(now);
            }
            return requestTimestamps.size();
        }
    }
}
