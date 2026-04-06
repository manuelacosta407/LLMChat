package com.sodium.llmchat;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class LLMChatPlugin extends JavaPlugin {

    private static LLMChatPlugin instance;
    private Map<String, ApiConfig> apiConfigs;
    private String defaultApiName;
    private List<String> helpMessage;
    private CooldownManager cooldownManager;
    private ChatLogger chatLogger;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        apiConfigs = new HashMap<>();
        loadApiConfigs();
        loadHelpMessage();
        cooldownManager = new CooldownManager(this);
        chatLogger = new ChatLogger(this);
        getCommand("llmchat").setExecutor(new LLMChatCommand(this));
        printStartupBanner();
        getLogger().info("LLMChat enabled with " + apiConfigs.size() + " API(s)");
    }

    private void printStartupBanner() {
        String[] banner = {
            "",
            "==========================================================================",
            "                                                                           ",
            "  /$$       /$$       /$$      /$$  /$$$$$$  /$$                   /$$    ",
            " | $$      | $$      | $$$    /$$$ /$$__  $$| $$                  | $$    ",
            " | $$      | $$      | $$$$  /$$$$| $$  \\__/| $$$$$$$   /$$$$$$  /$$$$$$  ",
            " | $$      | $$      | $$ $$/$$ $$| $$      | $$__  $$ |____  $$|_  $$_/  ",
            " | $$      | $$      | $$  $$$| $$| $$      | $$  \\ $$  /$$$$$$$  | $$    ",
            " | $$      | $$      | $$\\  $ | $$| $$    $$| $$  | $$ /$$__  $$  | $$ /$$",
            " | $$$$$$$$| $$$$$$$$| $$ \\/  | $$|  $$$$$$/| $$  | $$|  $$$$$$$  |  $$$$/",
            " |________/|________/|__/     |__/ \\______/ |__/  |__/ \\_______/   \\___/  ",
            "                                                                           ",
            "                                                                           ",
            "                    §b§lLLMChat Plugin Enabled!                           ",
            "                  §7Version: 1.2 | Author: Sodium_Sulfate                 ",
            " ==========================================================================",
            ""
        };
        for (String line : banner) {
            getLogger().info(line);
        }
    }

    @Override
    public void onDisable() {
        if (chatLogger != null) {
            chatLogger.shutdown();
        }
        getLogger().info("LLMChat disabled");
    }

    private void loadApiConfigs() {
        apiConfigs.clear();
        defaultApiName = getConfig().getString("default_api", "");
        
        ConfigurationSection apisSection = getConfig().getConfigurationSection("apis");
        if (apisSection == null) {
            getLogger().warning("No APIs configured in config.yml");
            return;
        }
        
        for (String apiName : apisSection.getKeys(false)) {
            ConfigurationSection apiSection = apisSection.getConfigurationSection(apiName);
            if (apiSection == null) continue;
            
            String url = apiSection.getString("url", "");
            String apiKey = apiSection.getString("api_key", "");
            String systemPrompt = apiSection.getString("system_prompt", "You are a helpful assistant.");
            String defaultModel = apiSection.getString("default_model", "");
            
            if (!validateApiConfig(apiName, url, apiKey)) {
                continue;
            }
            
            Map<String, String> models = new HashMap<>();
            ConfigurationSection modelsSection = apiSection.getConfigurationSection("models");
            if (modelsSection != null) {
                for (String modelName : modelsSection.getKeys(false)) {
                    ConfigurationSection modelSection = modelsSection.getConfigurationSection(modelName);
                    if (modelSection != null) {
                        String modelId = modelSection.getString("model_id", "");
                        if (!modelId.isEmpty()) {
                            models.put(modelName.toLowerCase(), modelId);
                        }
                    }
                }
            }
            
            if (models.isEmpty()) {
                getLogger().warning("API '" + apiName + "' has no models configured, skipping");
                continue;
            }
            
            if (defaultModel.isEmpty()) {
                defaultModel = models.keySet().iterator().next();
                getLogger().info("API '" + apiName + "' has no default model, using: " + defaultModel);
            } else {
                defaultModel = defaultModel.toLowerCase();
                if (!models.containsKey(defaultModel)) {
                    getLogger().warning("API '" + apiName + "' default model '" + defaultModel + "' not found, using: " + models.keySet().iterator().next());
                    defaultModel = models.keySet().iterator().next();
                }
            }
            
            ApiConfig config = new ApiConfig(apiName, url, apiKey, systemPrompt, defaultModel, models);
            apiConfigs.put(apiName.toLowerCase(), config);
            getLogger().info("Loaded API: " + apiName + " with " + models.size() + " model(s)");
        }
        
        if (!defaultApiName.isEmpty() && !apiConfigs.containsKey(defaultApiName.toLowerCase())) {
            getLogger().warning("Default API '" + defaultApiName + "' not found in configured APIs");
        }
    }

    private boolean validateApiConfig(String apiName, String url, String apiKey) {
        if (url == null || url.trim().isEmpty()) {
            getLogger().warning("API '" + apiName + "' has empty URL, skipping");
            return false;
        }
        
        if (!url.toLowerCase().startsWith("https://")) {
            getLogger().severe("API '" + apiName + "' must use HTTPS for security. HTTP URLs are not allowed.");
            return false;
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            getLogger().warning("API '" + apiName + "' has empty API key");
        } else if (apiKey.equals("YOUR_API_KEY") || apiKey.equals("YOUR_OPENAI_KEY")) {
            getLogger().warning("API '" + apiName + "' is using placeholder API key, please configure a real key");
        }
        
        return true;
    }

    @SuppressWarnings("deprecation")
    private void loadHelpMessage() {
        helpMessage = new ArrayList<>();
        List<String> rawMessages = getConfig().getStringList("help_message");
        for (String line : rawMessages) {
            helpMessage.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        if (helpMessage.isEmpty()) {
            helpMessage.add(ChatColor.YELLOW + "=== LLMChat 帮助 ===");
            helpMessage.add(ChatColor.AQUA + "/llmchat <消息> " + ChatColor.GRAY + "- 与AI对话");
            helpMessage.add(ChatColor.AQUA + "/llmchat list " + ChatColor.GRAY + "- 列出API和模型");
            helpMessage.add(ChatColor.AQUA + "/llmchat help " + ChatColor.GRAY + "- 显示帮助");
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        loadApiConfigs();
        loadHelpMessage();
        cooldownManager.loadConfig();
        getLogger().info("Configuration reloaded. " + apiConfigs.size() + " API(s) loaded.");
    }

    public static LLMChatPlugin getInstance() {
        return instance;
    }

    public ApiConfig getApiConfig(String name) {
        if (name == null || name.isEmpty()) {
            return apiConfigs.get(defaultApiName.toLowerCase());
        }
        return apiConfigs.get(name.toLowerCase());
    }

    public Map<String, ApiConfig> getAllApiConfigs() {
        return Collections.unmodifiableMap(apiConfigs);
    }

    public String getDefaultApiName() {
        return defaultApiName;
    }

    public List<String> getHelpMessage() {
        return Collections.unmodifiableList(helpMessage);
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public ChatLogger getChatLogger() {
        return chatLogger;
    }

    public static class ApiConfig {
        private final String name;
        private final String url;
        private final String apiKey;
        private final String systemPrompt;
        private final String defaultModel;
        private final Map<String, String> models;

        public ApiConfig(String name, String url, String apiKey, String systemPrompt, String defaultModel, Map<String, String> models) {
            this.name = name;
            this.url = url;
            this.apiKey = apiKey;
            this.systemPrompt = systemPrompt;
            this.defaultModel = defaultModel;
            this.models = models;
        }

        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getApiKey() { return apiKey; }
        public String getSystemPrompt() { return systemPrompt; }
        public String getDefaultModel() { return defaultModel; }
        
        public String getModelId(String modelName) {
            if (modelName == null || modelName.isEmpty()) {
                return models.get(defaultModel);
            }
            return models.get(modelName.toLowerCase());
        }
        
        public boolean hasModel(String modelName) {
            return models.containsKey(modelName.toLowerCase());
        }
        
        public Map<String, String> getModels() {
            return Collections.unmodifiableMap(models);
        }
    }
}
