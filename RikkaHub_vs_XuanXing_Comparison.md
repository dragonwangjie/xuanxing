# 玄星 vs RikkaHub 各模块详细对比与功能同步方案

> 玄星 (XuanXing v1.1.3, versionCode 8902) 是 RikkaHub (v2.4.x 基线) 的二次开发版本，继承 AGPL-3.0 协议。本文档详细对比两项目各模块，并列出需要同步到玄星的 RikkaHub 优点功能。

## 一、整体概览

| 维度 | RikkaHub (原项目) | 玄星 (本 fork) |
|------|-------------------|----------------|
| 最新版本 | 2.5.6 (2026) | 1.1.3 |
| 包名 | `me.rerere.rikkahub` | `com.xuanxing.app` |
| 基线 | 2.4.x 系列 | 2.4.x 系列 (git 初始提交) |
| 语言 | en/zh/zh-rTW/ja/ko-rKR/ru (6 种) | en/zh/zh-rTW/ja/ko-rKR/ru (6 种) |
| 主要定位 | 通用 AI 聊天客户端 | AI 逆向分析客户端 (以逆向工作台为主) |
| 核心增量 | - | 逆向工作台、内置逆向技能、AI 悬浮球、消息保活、自动压缩、持续工作 |

---

## 二、各模块详细对比

### 2.1 `app` 模块 (主应用)

| 子目录 | RikkaHub | 玄星 | 差异说明 |
|--------|----------|------|----------|
| `data/ai/transformers` | Template/ThinkTag/Regex/DocumentAsPrompt/Base64ImageToLocalFile/Ocr/WorkspaceReminder | 同左 + **Compress** (自动压缩摘要) | 玄星新增会话自动压缩 transformer |
| `data/ai/tools` | MemoryTools/SearchTools/ConversationTools/WorkspaceTools/SkillsTools + local 工具集 | 同左 + 逆向相关快捷消息 | RikkaHub 本地工具：Calendar/AskUser/LocalTools/ScreenTime/TimeInfo/TextToSpeech/Clipboard/Javascript/WebFetch |
| `data/datastore` | SettingsStore | SettingsStore(重命名为 PreferencesStore) + **自动压缩配置 + 保活/悬浮窗/持续工作开关 + 逆向预置 MCP 注入** | 玄星扩展配置集 |
| `data/sync` | WebDAV/S3 + importer(CherryStudio/Chatbox) | 同左 | 一致 |
| `data/repository` | 完整仓储层 | 同左 | 一致 |
| `data/db` + migrations | 迁移 1→22+ | 迁移 1→22+ | 玄星保留全部版本迁移 |
| `data/files` | FilesManager/SkillManager | 同左 | 一致 |
| `service/` | WebServerService/AiGeneratingFloatingWindow (仅前台可见浮窗)/ChatNotificationManager | **AiFloatingBall (系统级悬浮窗，SYSTEM_ALERT_WINDOW, 后台不消失)** / **ChatKeepAliveService (消息保活前台服务)** / ChatNotificationManager / ConversationSession | **玄星核心差异：系统级悬浮球 + 保活服务** |
| `ui/pages/` | assistant/chat/history/backup/search/webview/stats/translator/imggen/onboarding/favorite/extensions/setting/debug | 同左 + **home/HomeDashboardPage (玄星仪表盘)** + **setting/SettingAutoCompressPage** | 玄星用仪表盘取代原版樱花抽屉首页；新增自动压缩设置页 |
| `ui/pages/home` | (无独立首页，樱花抽屉) | HomeDashboardPage(欢迎区+快捷卡片+最近会话) + DefaultProviders + RecommendedProviders | 玄星首页完全重构 |
| `ui/theme/` | Material You 动态色 | 玄星紫主题 + 自定义色板 | 玄星品牌色定制 |
| `RikkaHubApp.kt` | Application 初始化(通知频道/临时文件清理/workspace 完整性检查/skill 预装/webserver 启动) | 同左 + **Onboarding 引导** + 逆向 MCP 预置注入 | 一致 |

**玄星已具备但 RikkaHub 基线缺失的功能：**
- 自动压缩会话 (按 token 阈值 200k 触发，保留最近 5 条)
- AI 悬浮球 (系统级 SYSTEM_ALERT_WINDOW，Handler 自驱动旋转，后台不卡)
- 消息保活前台服务 (ChatKeepAliveService，生成时占前台，熄屏继续跑)
- 持续工作模式 (AI 停下后自动追加"继续"，最多 N 轮)
- 逆向工作台 (预置 MT 管理器/玄星逆核/ProxyPin MCP 后端，默认助手为逆向)
- 内置逆向技能库与快捷消息
- MT 分析缓存清理工具
- 首页仪表盘 + 紫色品牌 UI

---

### 2.2 `ai` 模块 (AI SDK 抽象层)

| 子目录 | RikkaHub | 玄星 | 差异 |
|--------|----------|------|------|
| `ai/core/` | MessageRole/ReasoningLevel 等核心定义 | 同左 | 一致 |
| `ai/provider/` | OpenAI/Google/Claude 三 providers + ProviderManager | 同左 | 一致 |
| `ai/provider/providers/` | openai/vertex 子类 | 同左 | 一致 |
| `ai/registry/`, `ai/ui/`, `ai/util/` | 模型注册/UI 抽象/工具 | 同左 | 一致 |

**缺失的模型支持 (RikkaHub 2.5.5+ 新增)：**
- Claude Opus 5.5 / Sonnet 5.5
- Gemini 4
- Gemini Interactions API
- DeepSeek Flash
- Step-5
- GPT-6 识别

---

### 2.3 `common` 模块 (通用工具)

- 内容一致：Android 工具、JSON 处理、扩展函数等
- **缺失：AR 语言 RTL 适配工具 (RikkaHub 2.5.5 新增阿拉伯语)**

---

### 2.4 `document` 模块 (文档解析)

- MuPDF 实现，支持 PDF/DOCX/PPTX/EPUB
- **玄星已具备**，与 RikkaHub 基线一致

---

### 2.5 `search` 模块 (搜索 SDK)

| 搜索服务 | RikkaHub 列表 | 玄星列表 | 说明 |
|----------|---------------|----------|------|
| Exa/Tavily/Zhipu/LinkUp/Brave/Perplexity | ✅ | ✅ | 2.5.0 新增 Exa 高级参数 |
| Bing/SearXNG | ✅ | ✅ | 一致 |
| Bocha/Firecrawl/Grok/Jina | ✅ | ✅ | 一致 |
| Metaso/Ollama/RikkaHubSearch | ✅ | ✅ | 一致 |
| Serper/Tinyfish/CustomJsSearch | ✅ | ✅ | 一致 |

- 玄星 search 模块 26 个文件，与 RikkaHub 功能基本一致
- **缺失：Exa 高级参数支持 (2.5.0)**

---

### 2.6 `speech` 模块 (TTS/ASR)

| 功能 | RikkaHub | 玄星 | 说明 |
|------|----------|------|------|
| TTS providers | 多个 (含 Volcengine 2.5.2) | 同左 | RikkaHub 2.5.2 新增火山引擎 TTS |
| ASR providers | 多个 | 同左 | 一致 |
| Voice mode | 2.5.0 新增 (说完自动发送/连续对话/回复朗读) | ❌ | **缺失** |
| Message queue | 2.5.0 新增 (回复期间可继续输入/编辑撤销待发消息) | ❌ | **缺失** |
| 播放倍速 | 2.4.17 移动至常规设置 | ❌ | **缺失** |
| MiMo 快速选音色 | 2.4.14 新增 | ❌ | **缺失** |

---

### 2.7 `web` 模块 (嵌入式 Web 服务器 + web-ui 前端)

| 内容 | RikkaHub | 玄星 | 说明 |
|------|----------|------|------|
| Ktor 服务器 | ✅ | ✅ | 一致 |
| web-ui 前端 | React + React Router + Tailwind，持续更新 | ✅ (本地 pnpm 构建，同 RikkaHub web-ui) | 玄星跟随上游 web-ui |
| 终端标签/兼容性 | 2.5.0 终端标签，2.5.1 终端兼容模式 | ❌ | **缺失** |
| 网页/矢量图预览 | 2.5.1 工作区预览 | ❌ | **缺失** |
| 图表工具 (折线/柱状/散点图) | 2.5.6 新增 | ❌ | **缺失** |
| 输入框模糊/玻璃态背景 | 2.5.3 新增 | ❌ | **缺失** |
| 搜索会话 (当前助手/全部助手) | 2.4.17 新增 | ❌ | **缺失** |
| 网页聊天界面优化 | 2.5.0 优化 | ❌ | **缺失** |

---

### 2.8 `workspace` 模块 (proot 沙箱)

| 功能 | RikkaHub | 玄星 | 说明 |
|------|----------|------|------|
| proot 环境 | ✅ | ✅ | 一致 |
| 工作区 AGENTS.md 读取 | 2.5.2 新增 | ❌ | **缺失** |
| 长按多选 + 批量导出文件 | 2.5.3 新增 | ❌ | **缺失** |
| 文件缩略图预览 | 2.4.17 新增 | ❌ | **缺失** |
| 文件清理按时间范围筛选 | 2.4.15 新增 | ❌ | **缺失** |
| 存储管理界面优化 | 2.4.15 新增 | ❌ | **缺失** |
| 终端兼容模式 | 2.5.1 新增 | ❌ | **缺失** |

---

## 三、RikkaHub 优点功能同步到玄星的清单

### 高优先级 (用户体验影响大)

| # | 功能 | 版本 | 实现难度 | 备注 |
|---|------|------|----------|------|
| 1 | **语音模式**：说完自动发送/连续对话/回复朗读 | 2.5.0 | 中 | 集成到 speech 模块 |
| 2 | **消息队列**：回复期间可继续输入、编辑/撤销待发消息 | 2.5.0 | ✅ **设置开关已加入** | `DisplaySetting.enableMessageQueue` + 设置页 UI 已加；ChatInput 队列逻辑待后续接入 |
| 3 | **图表工具**：AI 聊天中直接展示折线/柱状/散点图 | 2.5.6 | 中 | 前端图表渲染 |
| 4 | **Provider 高级设置页**：自定义请求头 | 2.5.6 | 低 | SettingProviderDetailPage 扩展 |
| 5 | **搜索消息**：当前助手/全部助手切换 | 2.4.17 | 低 | 搜索框扩展 |
| 6 | **文件管理缩略图预览** | 2.4.17 | 中 | workspace 文件列表 |
| 7 | **自动重试开关** | 2.4.16 | ✅ **已实现** | `RetryInterceptor` + `DisplaySetting.enableAutoRetry` + OkHttp 注册 + 设置页开关 |
| 8 | **输入框玻璃态/模糊背景** | 2.5.3 | ✅ **已具备** | `settings.displaySetting.enableBlurEffect` 已在玄星 PreferencesStore 中，ChatInput.kt 使用 `hazeEffect` 实现 |
| 9 | **工作区 AGENTS.md 读取** | 2.5.2 | 低 | workspace 工具 |
| 10 | **长按多选 + 批量导出** | 2.5.3 | 中 | 文件列表多选态 |

### 中优先级 (功能增强)

| # | 功能 | 版本 | 备注 |
|---|------|------|------|
| 11 | Volcengine 火山引擎 TTS | 2.5.2 | TTS provider 扩展 |
| 12 | 模型列表按提供商折叠 | 2.5.6 | Model 选择器 UI |
| 13 | 导出对话不含思考过程 | 2.5.6 | ExportSerializer |
| 14 | 复制助手同时复制记忆 | 2.5.6 | Assistant 复制 |
| 15 | Gemini Interactions API 支持 | 2.5.6 | GoogleProvider |
| 16 | 工作区网页/矢量图预览 | 2.5.1 | 预览组件 |
| 17 | 终端兼容模式 | 2.5.1 | 终端模拟器 |
| 18 | 自定义时间提醒间隔 | 2.5.1 | TimeReminderTransformer |
| 19 | 助手提问自定义文字回复 | 2.5.1 | Assistant 交互 |
| 20 | 工具执行期间查看调用详情 | 2.5.1 | Tool part UI |
| 21 | 技能创建助手技能 | 2.5.5 | 内置 skill |
| 22 | 技能页搜索 (名称/描述) | 2.5.5 | Skills 页面 |
| 23 | 数据恢复页 (从聊天记录恢复助手) | 2.5.5 | backup 扩展 |
| 24 | 会话分支标题自动继承序号 | 2.5.3 | 分支生成逻辑 |
| 25 | 图片生成快捷方式 | 2.5.6 | 快捷入口 |
| 26 | 阿拉伯语 + RTL 适配 | 2.5.5 | i18n (工作量较大) |
| 27 | 全模型支持更新 | Claude Opus 5.5/Sonnet 5.5/Gemini 4/DeepSeek Flash/Step-5/GPT-6 | 配置文件 |

### 低优先级 (维护性)

| # | 功能 | 版本 | 备注 |
|---|------|------|------|
| 28 | 播放倍速移至常规设置 | 2.4.17 | 设置迁移 |
| 29 | 快速模型配置简化 (思考级别) | 2.4.16 | 设置重构 |
| 30 | 改进备份恢复可靠性 | 2.4.17 | 已有，继续跟进 |
| 31 | MCP OAuth 兼容性改进 | 2.4.15/2.5.6 | 已有，继续跟进 |
| 32 | Gemini 云端+本地工具同时用 | 2.4.15 | GeminiProvider |
| 33 | 修复技能保存崩溃/GitHub 导入损坏 | 2.5.5 | Bugfix |

---

## 四、玄星独有 / RikkaHub 基线缺失的保留清单

以下功能为玄星独有，**不应回退**：
1. 逆向工作台 (预置 MCP 后端 + 助手 + 技能 + 快捷消息)
2. 自动压缩会话 (SettingsStore + Transformer)
3. AI 悬浮球 (AiFloatingBall + SYSTEM_AUTO 策略)
4. 消息保活前台服务 (ChatKeepAliveService)
5. 持续工作模式 (ContinuousWork)
6. MT 分析缓存清理
7. 首页仪表盘 + 紫色品牌主题
8. Onboarding 引导
9. 默认助手设为逆向工作台

---

## 五、同步实施路线建议

### 第一阶段 (快速见效，2-3 天)
- Provider 高级设置页 (自定义请求头) — 直接复用 RikkaHub 实现
- 自动重试开关 — RequestLoggingInterceptor 扩展
- 搜索消息切换 (当前/全部助手) — 搜索框扩展
- 输入框玻璃态背景 — ChatMessageInputPage 背景改造
- 工作区 AGENTS.md 读取 — workspace 工具读取

### 第二阶段 (核心体验，5-7 天)
- 语音模式 (说完自动发送/连续对话/朗读)
- 消息队列 (输入期间可编辑/撤销)
- Provider 高级设置页完整实现
- 文件缩略图预览

### 第三阶段 (增强功能，1-2 周)
- 图表工具 (前端 Chart)
- 长按多选 + 批量导出
- 模型折叠/导出排除思考/复制助手含记忆
- Gemini Interactions API / 新模型支持

---

## 六、技术注意事项

1. **AGPL-3.0 合规**：所有同步代码必须保留原作者署名，衍生作品同样 AGPL-3.0 开源
2. **命名空间**：玄星源码 package 保持 `me.rerere.rikkahub`，仅 applicationId 为 `com.xuanxing.app`
3. **语言支持**：新增 UI 需同步 en/zh/zh-rTW/ja/ko-rKR/ru 六语种 strings.xml
4. **R8 混淆**：玄星已关闭 R8 混淆 (池鸳魔改版)，同步新类时无混淆风险
5. **web-ui 同步**：web 模块在 preBuild 阶段从 web-ui/ 构建，可先拉取上游 web-ui 最新代码再构建
6. **SettingsStore 扩展**：新增设置项需同时更新 VERSION 迁移 + PreferencesStore 读写
7. **测试**：新 UI 功能建议补充 Android 仪器测试

---

*文档生成时间：2026-10-13 | 玄星版本：1.1.3 (v1.1.7 tag) | RikkaHub 基线：v2.4.x | RikkaHub 最新：v2.5.6*