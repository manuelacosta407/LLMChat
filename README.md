<div align="center">

# LLMChat

**一个强大的 Minecraft 服务器 AI 聊天插件**

让玩家在游戏中与各种大语言模型 (LLM) 进行对话

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20%2B-green?style=flat-square&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue?style=flat-square)](./LICENSE)
[![Version](https://img.shields.io/badge/Version-1.1-brightgreen?style=flat-square)](https://github.com/SodiumSulfate/LLMChat/releases)
[![Website](https://img.shields.io/badge/作者官网-sodium.ren-blue?style=flat-square&logo=google-chrome&logoColor=white)](https://sodium.ren/)

[English](#english) | [中文文档](#中文文档) | [作者官网](https://sodium.ren/)

</div>

---

## 中文文档

### 简介

你是否想过让 Minecraft 服务器中的玩家能够直接与 AI 对话？LLMChat 让这一切变得简单。

LLMChat 是一个为 Minecraft 服务器设计的 AI 聊天插件，支持多种大语言模型 API，让玩家可以在游戏中与 AI 进行自然对话。无论是回答游戏问题、提供服务器帮助，还是简单的闲聊，LLMChat 都能胜任。

> [!TIP]
> 支持所有兼容 OpenAI API 格式的服务，包括 OpenAI、SiliconFlow、DeepSeek、Azure OpenAI 等。

### 功能特性

| 特性 | 描述 |
|:----:|:-----|
| 🔌 **多 API 支持** | 同时配置多个 LLM API（如 OpenAI、SiliconFlow 等） |
| 🎭 **多模型选择** | 每个 API 可配置多个模型，玩家可自由选择 |
| ⌨️ **灵活命令** | 支持指定 API 和模型进行对话 |
| ⏱️ **冷却管理** | 可配置的请求频率限制，防止 API 滥用 |
| 📋 **白名单系统** | 特定玩家可绕过冷却限制 |
| 📝 **聊天日志** | 自动记录所有对话，按日期分文件存储 |
| 🩺 **健康检查** | 控制台命令检查所有 API 和模型的可用性 |
| 🔄 **热重载** | 无需重启服务器即可重新加载配置 |
| 🔒 **安全设计** | 强制 HTTPS 连接，敏感信息自动脱敏 |

### 环境要求

| 依赖 | 版本要求 |
|:----:|:--------:|
| Minecraft Server | Paper 1.20+ (或 Spigot/Bukkit 兼容) |
| Java | 21 或更高版本 |

### 安装

1. 从 [Releases](https://github.com/SodiumSulfate/LLMChat/releases) 页面下载最新的 `LLMChat-x.x.jar`
2. 将 jar 文件放入服务器的 `plugins` 目录
3. 启动服务器，插件会自动生成配置文件
4. 编辑 `plugins/LLMChat/config.yml` 配置你的 API
5. 使用 `/llmchat reload` 重载配置（控制台）

> [!WARNING]
> 请勿将包含真实 API Key 的 `config.yml` 上传到公开仓库！

### 配置说明

```yaml
# 默认使用的 API
default_api: "siliconflow"

# 帮助信息（支持 Minecraft 颜色代码）
help_message:
  - "&e========== LLMChat 帮助 =========="
  - "&b/llmchat <消息> &7- 使用默认API和模型与AI对话"
  - "&b/llmchat -api:<名称> <消息> &7- 使用指定API"
  - "&b/llmchat -model:<名称> <消息> &7- 使用指定模型"
  - "&e=================================="

# 冷却时间配置
cooldown:
  whitelist:          # 绕过冷却的玩家列表
    - "admin"
  default:            # 默认冷却设置
    max_requests_per_minute: 5
    cooldown_seconds: 60
  models:             # 特定模型的冷却设置
    qwen:
      max_requests_per_minute: 10
      cooldown_seconds: 30

# API 配置
apis:
  siliconflow:
    url: "https://api.siliconflow.cn/v1/chat/completions"
    api_key: "YOUR_API_KEY"
    default_model: "qwen"
    system_prompt: "你是Minecraft服务器中的AI助手，说话简短（不超过80字），用中文回答。"
    models:
      qwen:
        model_id: "Qwen/Qwen3-8B"
      deepseek:
        model_id: "deepseek-ai/DeepSeek-V3"
```

### 命令

| 命令 | 描述 | 权限 |
|:-----|:-----|:----:|
| `/llmchat <消息>` | 使用默认 API 和模型对话 | `llmchat.use` |
| `/llmchat -api:<名称> <消息>` | 使用指定 API 对话 | `llmchat.use` |
| `/llmchat -model:<名称> <消息>` | 使用指定模型对话 | `llmchat.use` |
| `/llmchat -api:<名称> -model:<名称> <消息>` | 同时指定 API 和模型 | `llmchat.use` |
| `/llmchat list` | 列出所有可用的 API 和模型 | `llmchat.list` |
| `/llmchat help` | 显示帮助信息 | - |
| `/llmchat check` | 检查所有 API 可用性（仅控制台） | `llmchat.admin` |
| `/llmchat reload` | 重载配置（仅控制台） | `llmchat.admin` |

### 权限

| 权限 | 描述 | 默认 |
|:-----|:-----|:----:|
| `llmchat.use` | 使用 AI 对话功能 | 所有人 |
| `llmchat.list` | 查看 API 和模型列表 | 所有人 |
| `llmchat.admin` | 管理员命令 (reload, check) | OP |
| `llmchat.bypass.cooldown` | 绕过冷却时间限制 | OP |
| `llmchat.*` | 所有 LLMChat 权限 | OP |

### 支持的 API 服务

理论上支持所有兼容 OpenAI API 格式的服务：

| 服务商 | 状态 | 备注 |
|:-------|:----:|:-----|
| [OpenAI](https://platform.openai.com/) | ✅ | GPT-3.5, GPT-4 |
| [SiliconFlow](https://cloud.siliconflow.cn/) | ✅ | Qwen, DeepSeek 等 |
| [DeepSeek](https://www.deepseek.com/) | ✅ | DeepSeek 系列 |
| [Azure OpenAI](https://azure.microsoft.com/openai) | ✅ | 需配置正确的 endpoint |
| [Cloudflare Workers AI](https://developers.cloudflare.com/workers-ai/) | ✅ | - |
| [通义千问](https://help.aliyun.com/document_detail/2400395.html) | ✅ | 阿里云 |
| [智谱 AI](https://bigmodel.cn) | ✅ | GLM 系列 |
| [Moonshot](https://platform.moonshot.cn/) | ✅ | Kimi 系列 |
| 其他 OpenAI 兼容 API | ✅ | 只需配置正确的 URL |

### 构建

```bash
# 克隆仓库
git clone https://github.com/SodiumSulfate/LLMChat.git
cd LLMChat

# 使用 Maven 构建
mvn clean package

# 构建产物位于 target/LLMChat-x.x.jar
```

### 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

> [!NOTE]
> 提交 PR 前请确保代码风格与现有代码一致，并通过编译测试。

### 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 开源协议。

**重要条款**：
- ✅ 可以自由使用、修改和分发
- ⚠️ 修改后的版本**必须开源**并以相同协议发布
- ⚠️ 引用或整合本代码的项目**必须开源**并以相同协议发布
- ⚠️ 必须保留原始版权声明和许可证

---

## English

### Introduction

Have you ever wanted players on your Minecraft server to chat directly with AI? LLMChat makes it simple.

LLMChat is an AI chat plugin designed for Minecraft servers, supporting multiple LLM APIs, allowing players to have natural conversations with AI in-game. Whether answering game questions, providing server help, or just casual chat, LLMChat handles it all.

> [!TIP]
> Supports all OpenAI API compatible services, including OpenAI, SiliconFlow, DeepSeek, Azure OpenAI, and more.

### Features

| Feature | Description |
|:-------:|:------------|
| 🔌 **Multiple API Support** | Configure multiple LLM APIs simultaneously (OpenAI, SiliconFlow, etc.) |
| 🎭 **Multiple Model Selection** | Each API can have multiple models for players to choose from |
| ⌨️ **Flexible Commands** | Specify API and model for conversations |
| ⏱️ **Cooldown Management** | Configurable rate limiting to prevent API abuse |
| 📋 **Whitelist System** | Specific players can bypass cooldown restrictions |
| 📝 **Chat Logging** | Automatic conversation logging with daily file rotation |
| 🩺 **Health Check** | Console command to check availability of all APIs and models |
| 🔄 **Hot Reload** | Reload configuration without server restart |
| 🔒 **Security Design** | Enforced HTTPS connections, automatic sensitive data sanitization |

### Requirements

| Dependency | Version |
|:----------:|:-------:|
| Minecraft Server | Paper 1.20+ (or Spigot/Bukkit compatible) |
| Java | 21 or higher |

### Installation

1. Download the latest `LLMChat-x.x.jar` from [Releases](https://github.com/SodiumSulfate/LLMChat/releases)
2. Place the jar file in your server's `plugins` directory
3. Start the server, the plugin will generate configuration files
4. Edit `plugins/LLMChat/config.yml` to configure your APIs
5. Use `/llmchat reload` to reload configuration (console only)

> [!WARNING]
> Never upload `config.yml` containing real API keys to public repositories!

### Commands

| Command | Description | Permission |
|:--------|:------------|:----------:|
| `/llmchat <message>` | Chat with default API and model | `llmchat.use` |
| `/llmchat -api:<name> <message>` | Use specified API | `llmchat.use` |
| `/llmchat -model:<name> <message>` | Use specified model | `llmchat.use` |
| `/llmchat list` | List all available APIs and models | `llmchat.list` |
| `/llmchat help` | Show help message | - |
| `/llmchat check` | Check API availability (console only) | `llmchat.admin` |
| `/llmchat reload` | Reload configuration (console only) | `llmchat.admin` |

### Permissions

| Permission | Description | Default |
|:-----------|:------------|:-------:|
| `llmchat.use` | Use AI chat feature | Everyone |
| `llmchat.list` | View API and model list | Everyone |
| `llmchat.admin` | Admin commands (reload, check) | OP |
| `llmchat.bypass.cooldown` | Bypass cooldown restrictions | OP |
| `llmchat.*` | All LLMChat permissions | OP |

### Supported APIs

Theoretically supports all OpenAI API compatible services:

| Provider | Status | Notes |
|:---------|:------:|:------|
| [OpenAI](https://platform.openai.com/) | ✅ | GPT-3.5, GPT-4 |
| [SiliconFlow](https://cloud.siliconflow.cn/) | ✅ | Qwen, DeepSeek, etc. |
| [DeepSeek](https://www.deepseek.com/) | ✅ | DeepSeek series |
| [Azure OpenAI](https://azure.microsoft.com/openai) | ✅ | Requires correct endpoint |
| [Cloudflare Workers AI](https://developers.cloudflare.com/workers-ai/) | ✅ | - |
| Other OpenAI-compatible APIs | ✅ | Just configure the correct URL |

### Building

```bash
# Clone the repository
git clone https://github.com/SodiumSulfate/LLMChat.git
cd LLMChat

# Build with Maven
mvn clean package

# Output: target/LLMChat-x.x.jar
```

### Contributing

Issues and Pull Requests are welcome!

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Create a Pull Request

> [!NOTE]
> Please ensure your code style is consistent with existing code and passes compilation before submitting a PR.

### License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

**Key Terms**:
- ✅ Free to use, modify, and distribute
- ⚠️ Modified versions **must be open source** under the same license
- ⚠️ Projects that use or integrate this code **must be open source** under the same license
- ⚠️ Original copyright notice and license must be preserved

---

## Star History

<a href="https://www.star-history.com/?repos=SodiumSulfate%2FLLMChat&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=SodiumSulfate/LLMChat&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=SodiumSulfate/LLMChat&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=SodiumSulfate/LLMChat&type=date&legend=top-left" />
 </picture>
</a>

---

<div align="center">

**Made with ❤️ by [Sodium_Sulfate](https://github.com/SodiumSulfate)**

</div>
