# 玄星 (XuanXing)

> 一个运行在 Android 上的 AI 大模型聊天客户端，支持多家供应商切换、MCP 工具调用、
> 语音、Markdown 渲染等。本项目在 [RikkaHub](https://github.com/rikkahub/rikkahub) 基础上二次开发，
> 增加了逆向工作台、内置技能、悬浮球、消息保活等功能。

---

## ⚠️ 项目来源与开源声明（重要）

**本项目是基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 二次开发的衍生作品。**

RikkaHub 使用 **AGPL-3.0** 许可证发布，因此本项目（玄星）同样遵循 **AGPL-3.0** 开源，
完整源码在本仓库/网盘公开，任何人可自由查看、修改、再分发。

- 原项目：RikkaHub —— https://github.com/rikkahub/rikkahub
- 原项目许可证：[AGPL-3.0](LICENSE)

**特别感谢 RikkaHub 原作者**打下的完整 AI 聊天客户端基础。玄星在其之上做了功能扩展与界面调整，
但核心框架、绝大部分聊天/供应商/MCP/TTS 代码源自 RikkaHub，版权与署名归原作者所有。

> 若原作者对本二开有任何异议，请联系我，我会积极配合处理。

---

## 二次开发新增/改动（相对 RikkaHub）

- **逆向工作台**：预置连接本机逆向 MCP 后端（MT 管理器 / 玄星逆核 / ProxyPin），
  一键探测端口，配套逆向技能与 system prompt。
- **内置技能库**：打包了一批安卓逆向/安全分析技能文档。
- **玄星高级功能**：AI 生成悬浮球、消息保活前台服务、自动压缩会话、持续工作等。
- **界面与品牌**：玄星紫色主题、首页仪表盘、底部导航等 UI 调整。
- 品牌信息（应用名、图标、包名 applicationId）改为玄星。

其余的核心能力（多供应商、模型管理、MCP 客户端、TTS/ASR、Markdown、备份同步等）
均来自 RikkaHub，遵循其原有实现与许可证。

---

## 构建

```bash
# 需要 JDK 21 + Android SDK
./gradlew assembleRelease
```

web-ui 前端模块需先安装依赖（pnpm）。详见各模块说明与原 RikkaHub 文档
（[AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md)）。

---

## 许可证

本项目依据 [GNU Affero General Public License v3.0](LICENSE) 发布（继承自 RikkaHub）。
你可以自由使用、修改、再分发本项目，但衍生作品必须同样以 AGPL-3.0 开源并保留署名。

第三方依赖、submodule 等仍分别遵循各自上游许可证，AGPL-3.0 声明不替代其原有条款。
