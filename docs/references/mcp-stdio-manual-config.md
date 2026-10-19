# 手动配置 stdio MCP 后端（以 REA 为例）

> 状态说明：逆向工作台**不预置** REA 等 stdio 后端（方案 P2-c）。
> 原因：stdio 命令依赖本机可解析的执行环境，App 无法预知用户设备上装了什么。
> 裸命令名预置曾在真机实测为 `exit 127 (command-not-found)`——静默坏开关比没有开关更糟。
> 本文描述的手动路径是官方支持的正路，配置项在 设置 → MCP → 添加服务器 → stdio 中全部可填。

## REA 是什么、装在哪一层

[REA](https://github.com/morluto/rea)（npm 包 `rea-agents`）是纯 Node.js CLI，MCP 传输**只有 stdio**
（`rea mcp add` 仅注册 `command`，无 HTTP/SSE 模式）。它要求 Node ≥22.19。
Android 宿主（`/system/bin/sh`）没有 node，也不共享任何 rootfs 的 PATH，所以必须把命令
放进**带 Node 的 rootfs 工作区**执行——玄星的 stdio MCP 支持正是这样路由的。

## 第 1 步：准备带 Node.js 的 rootfs 工作区

在 设置 → 工作区 准备一个 proot Linux 环境（导入/自建均可），要求镜像内有可用的
`node` 与 `npm`（`node --version` 能出版本号即就绪；Debian/Ubuntu 系可 `apt install nodejs npm`，
或参考 [NodeSource](https://github.com/nodesource/distributions) 装 22.x）。

root 目录名是自定义的（合法字符 `[A-Za-z0-9._-]+`，物理路径
`Android/data/<包名>/files/workspaces/<root名>/linux`）。
**下一步填的 Rootfs 名必须与此完全一致**——这层对应关系由你手动维护（P2-c 的已知代价）。

## 第 2 步：rootfs 内安装 REA

进入工作区终端执行：

```bash
npm install -g rea-agents
rea --version        # 能出版本号即成功
```

## 第 3 步：玄星 MCP 页添加服务器

设置 → MCP → 添加（stdio 类型），四个字段：

| 字段 | 填写 | 说明 |
|---|---|---|
| 名称 | `ReaMcp` | 仅限字母数字（启用校验） |
| 命令 | `rea mcp` | UI 会把整行按空白拆成 command+args |
| Rootfs 工作区 | 第 1 步的 root 名 | **留空 = 在 Android 宿主执行，裸 `rea` 必然 127** |
| 工具按需加载 | 开 | REA 有 200+ 工具，全量 schema 约 20 万 token；懒加载后未展开仅百余 token |

## 已知限制（诚实清单）

- **无预置、无探测**：stdio 没有端口可探测（区别于 MT APK MCP/ProxyPin 的一键探测）；root 名写错时启用会报错，属预期。
- **带空格/引号的参数不支持**：命令输入框按空白硬拆。需要时用配置 JSON 直接写 `args` 数组（带引号的 token 会被逐 token 转义安全传递，但 UI 输入框做不到）。
- **REA 的部分高级能力在 arm64 上不可用**：依赖 x64 原生桥的 x86 Ghidra/pwntools/mitmproxy 联动会失败；静态分析类（APK 解码、资源、证据链）可用。
- 命令与参数全部经过 shell 单引号转义后才拼进 `sh -c`——往命令里塞 `;`、`&&` 只会得到一个找不到的文件名，不会被执行（真机已验证）。

## 验证

```
配置保存 → 开关启用 → 状态变"已连接"且工具列表出现 rea 工具即成功。
失败排查顺序：root 名拼写 → rootfs 内 which rea → node --version ≥22.19。
```

调试期可用零依赖假后端验证传输链路本身（`tools/mcp_probe/fake_mcp.sh`，
推到设备后 命令填 `sh /data/local/tmp/fake_mcp.sh`、Rootfs 留空），
握手返回 `fake-sh-mcp` 即说明 stdio 拉起/转义/JSON-RPC 全链路正常。
