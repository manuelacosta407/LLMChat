package com.sodium.llmchat;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class LLMChatCommand implements CommandExecutor {

    private static final Gson GSON = new Gson();
    private static final HttpClient SHARED_CLIENT = createSecureHttpClient();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(api[_-]?key|token|authorization|bearer|secret|password|passwd|pwd|private[_-]?key|access[_-]?token)" +
        "\\s*[:=]\\s*['\"]?[^\\s,'\"<>]+", 
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_VALUE_PATTERN = Pattern.compile(
        "\"(api[_-]?key|token|authorization|bearer|secret|password|passwd|pwd|private[_-]?key|access[_-]?token)\"\\s*:\\s*\"[^\"]*\"",
        Pattern.CASE_INSENSITIVE
    );

    private final LLMChatPlugin plugin;

    public LLMChatCommand(LLMChatPlugin plugin) {
        this.plugin = plugin;
    }

    private static HttpClient createSecureHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("llmchat.list")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            listApis(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("§c此命令仅限控制台使用");
                return true;
            }
            if (!sender.hasPermission("llmchat.admin")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            checkAllApis();
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("§c此命令仅限控制台使用");
                return true;
            }
            if (!sender.hasPermission("llmchat.admin")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            plugin.reloadConfiguration();
            sender.sendMessage("§a配置已重新加载");
            return true;
        }

        if (!sender.hasPermission("llmchat.use")) {
            sender.sendMessage("§c你没有权限使用AI对话功能");
            return true;
        }

        ParsedCommand parsed = parseArgs(args);
        
        if (parsed.message == null || parsed.message.isEmpty()) {
            sender.sendMessage("§c请输入消息内容");
            return true;
        }

        LLMChatPlugin.ApiConfig apiConfig = plugin.getApiConfig(parsed.apiName);
        if (apiConfig == null) {
            sender.sendMessage("§c未找到API: " + (parsed.apiName == null ? plugin.getDefaultApiName() : parsed.apiName));
            return true;
        }

        String modelId = apiConfig.getModelId(parsed.modelName);
        if (modelId == null) {
            sender.sendMessage("§c未找到模型: " + parsed.modelName + " (API: " + apiConfig.getName() + ")");
            sender.sendMessage("§7可用模型: " + String.join(", ", apiConfig.getModels().keySet()));
            return true;
        }

        String displayName = parsed.modelName != null ? parsed.modelName : apiConfig.getDefaultModel();

        CooldownManager cooldownManager = plugin.getCooldownManager();
        
        if (sender instanceof Player && !sender.hasPermission("llmchat.bypass.cooldown")) {
            String playerName = ((Player) sender).getName();
            if (!cooldownManager.canUse(playerName, displayName)) {
                int remaining = cooldownManager.getRemainingCooldownSeconds(displayName);
                sender.sendMessage("§c该模型正在冷却中，请" + remaining + "秒后再试");
                return true;
            }
            cooldownManager.recordUse(displayName);
        }

        String playerName = sender instanceof Player ? ((Player) sender).getName() : "Console";
        StringBuilder cmdBuilder = new StringBuilder("/llmchat");
        for (String arg : args) {
            cmdBuilder.append(" ").append(arg);
        }
        plugin.getChatLogger().log(playerName, cmdBuilder.toString());

        sender.sendMessage("§7AI思考中... §8[API: " + apiConfig.getName() + " | 模型: " + displayName + "]");

        final String finalModelId = modelId;
        final LLMChatPlugin.ApiConfig finalApiConfig = apiConfig;
        final String finalMessage = parsed.message;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String reply = requestAI(finalApiConfig, finalModelId, finalMessage);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§bAI: §f" + reply);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§c调用AI时出错: " + e.getMessage());
                });
                plugin.getLogger().warning("AI request failed: " + e.getClass().getSimpleName() + " - " + sanitizeMessage(e.getMessage()));
            }
        });

        return true;
    }

    private static class ParsedCommand {
        String apiName = null;
        String modelName = null;
        String message = null;
    }

    private ParsedCommand parseArgs(String[] args) {
        ParsedCommand result = new ParsedCommand();
        StringBuilder messageBuilder = new StringBuilder();
        
        for (String arg : args) {
            if (arg.startsWith("-api:")) {
                result.apiName = arg.substring(5);
            } else if (arg.startsWith("-model:")) {
                result.modelName = arg.substring(7);
            } else if (!arg.startsWith("-")) {
                if (messageBuilder.length() > 0) messageBuilder.append(" ");
                messageBuilder.append(arg);
            }
        }
        
        result.message = messageBuilder.length() > 0 ? messageBuilder.toString() : null;
        return result;
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getHelpMessage()) {
            sender.sendMessage(line);
        }
    }

    private void listApis(CommandSender sender) {
        sender.sendMessage("§e=== 可用的API和模型 ===");
        Map<String, LLMChatPlugin.ApiConfig> apis = plugin.getAllApiConfigs();
        
        if (apis.isEmpty()) {
            sender.sendMessage("§c没有配置任何API");
            return;
        }

        for (Map.Entry<String, LLMChatPlugin.ApiConfig> entry : apis.entrySet()) {
            LLMChatPlugin.ApiConfig config = entry.getValue();
            String defaultMarker = entry.getKey().equalsIgnoreCase(plugin.getDefaultApiName()) ? " §a(默认)" : "";
            sender.sendMessage("§6API: §f" + config.getName() + defaultMarker);
            
            for (Map.Entry<String, String> model : config.getModels().entrySet()) {
                String modelDefaultMarker = model.getKey().equalsIgnoreCase(config.getDefaultModel()) ? " §a(默认)" : "";
                sender.sendMessage("  §7- §b" + model.getKey() + " §8(" + model.getValue() + ")" + modelDefaultMarker);
            }
        }
    }

    private void checkAllApis() {
        Map<String, LLMChatPlugin.ApiConfig> apis = plugin.getAllApiConfigs();
        
        if (apis.isEmpty()) {
            plugin.getLogger().warning("没有配置任何API");
            return;
        }

        List<ApiCheckTask> tasks = new ArrayList<>();
        for (LLMChatPlugin.ApiConfig config : apis.values()) {
            for (Map.Entry<String, String> model : config.getModels().entrySet()) {
                tasks.add(new ApiCheckTask(config, model.getKey(), model.getValue()));
            }
        }

        plugin.getLogger().info("开始检查 " + tasks.size() + " 个API/模型组合...");
        
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        
        for (ApiCheckTask task : tasks) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    checkApi(task.config, task.modelId);
                    plugin.getLogger().info("[✓] " + task.config.getName() + "/" + task.modelName + " - 可用");
                    success.incrementAndGet();
                } catch (Exception e) {
                    plugin.getLogger().warning("[✗] " + task.config.getName() + "/" + task.modelName + " - 失败: " + sanitizeMessage(e.getMessage()));
                } finally {
                    int done = completed.incrementAndGet();
                    if (done == tasks.size()) {
                        plugin.getLogger().info("检查完成: " + success.get() + "/" + tasks.size() + " 可用");
                    }
                }
            });
        }
    }

    private void checkApi(LLMChatPlugin.ApiConfig apiConfig, String modelId) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("model", modelId);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "ping");
        messages.add(user);
        root.add("messages", messages);

        String body = GSON.toJson(root);
        HttpRequest request = buildHttpRequest(apiConfig, body, CHECK_TIMEOUT);

        HttpResponse<String> response = SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("HTTP " + response.statusCode());
        }
    }

    private static class ApiCheckTask {
        final LLMChatPlugin.ApiConfig config;
        final String modelName;
        final String modelId;

        ApiCheckTask(LLMChatPlugin.ApiConfig config, String modelName, String modelId) {
            this.config = config;
            this.modelName = modelName;
            this.modelId = modelId;
        }
    }

    private HttpRequest buildHttpRequest(LLMChatPlugin.ApiConfig apiConfig, String body, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiConfig.getUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout)
                .build();
    }

    private String requestAI(LLMChatPlugin.ApiConfig apiConfig, String modelId, String message) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("model", modelId);

        JsonArray messages = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", apiConfig.getSystemPrompt());
        messages.add(sys);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", message);
        messages.add(user);

        root.add("messages", messages);

        String body = GSON.toJson(root);
        HttpRequest request = buildHttpRequest(apiConfig, body, REQUEST_TIMEOUT);

        HttpResponse<String> response = SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("HTTP " + response.statusCode() + ": " + sanitizeResponseBody(response.body()));
        }

        return parseAIResponse(response.body());
    }

    private String parseAIResponse(String responseBody) throws Exception {
        JsonObject json;
        try {
            JsonElement rootElement = JsonParser.parseString(responseBody);
            if (rootElement == null || !rootElement.isJsonObject()) {
                throw new Exception("API响应不是有效的JSON对象");
            }
            json = rootElement.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new Exception("无效的JSON响应: " + sanitizeResponseBody(responseBody));
        }

        if (!json.has("choices")) {
            throw new Exception("API响应格式错误: 缺少choices字段");
        }
        
        JsonElement choicesElement = json.get("choices");
        if (choicesElement == null || choicesElement.isJsonNull()) {
            throw new Exception("API响应格式错误: choices字段为空");
        }
        
        if (!choicesElement.isJsonArray()) {
            throw new Exception("API响应格式错误: choices不是数组");
        }
        
        JsonArray choices = choicesElement.getAsJsonArray();
        if (choices.isEmpty()) {
            throw new Exception("API返回空的choices数组");
        }

        JsonElement firstChoice = choices.get(0);
        if (firstChoice == null || !firstChoice.isJsonObject()) {
            throw new Exception("API响应格式错误: choices[0]无效");
        }

        JsonObject choiceObj = firstChoice.getAsJsonObject();
        if (!choiceObj.has("message")) {
            throw new Exception("API响应格式错误: 缺少message字段");
        }
        
        JsonElement messageElement = choiceObj.get("message");
        if (messageElement == null || messageElement.isJsonNull() || !messageElement.isJsonObject()) {
            throw new Exception("API响应格式错误: message不是有效对象");
        }

        JsonObject messageObj = messageElement.getAsJsonObject();
        if (!messageObj.has("content")) {
            throw new Exception("API响应格式错误: 缺少content字段");
        }
        
        JsonElement contentElement = messageObj.get("content");
        if (contentElement == null || contentElement.isJsonNull()) {
            throw new Exception("API响应格式错误: content字段为空");
        }

        return contentElement.getAsString();
    }

    private static String sanitizeResponseBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String sanitized = SENSITIVE_PATTERN.matcher(body).replaceAll("[REDACTED]");
        sanitized = JSON_VALUE_PATTERN.matcher(sanitized).replaceAll("\"$1\":\"[REDACTED]\"");
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        return sanitized;
    }

    private static String sanitizeMessage(String message) {
        if (message == null) {
            return "Unknown error";
        }
        String sanitized = SENSITIVE_PATTERN.matcher(message).replaceAll("[REDACTED]");
        sanitized = JSON_VALUE_PATTERN.matcher(sanitized).replaceAll("\"$1\":\"[REDACTED]\"");
        return sanitized;
    }
}
