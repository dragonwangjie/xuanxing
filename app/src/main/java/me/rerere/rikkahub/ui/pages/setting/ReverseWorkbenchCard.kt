package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Link01
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.serverUrl
import me.rerere.rikkahub.data.datastore.MT_APK_MCP_SERVER_ID
import me.rerere.rikkahub.data.datastore.PROXYPIN_MCP_SERVER_ID
import me.rerere.rikkahub.data.datastore.SOMCP_SO_MCP_SERVER_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.isPackageInstalled
import me.rerere.rikkahub.utils.launchOrOpenMarket
import java.net.Socket
import java.net.InetSocketAddress

// 相关 App 包名，用于一键拉起与安装检测
private const val PKG_MT = "bin.mt.plus"
private const val PKG_MT_CANARY = "bin.mt.plus.canary"
private const val PKG_NIEHE = "com.xuanxingnihe" // 玄星逆核（聚合逆向后端）
private const val PKG_PROXYPIN = "com.network.proxy"

/**
 * 池鸳魔改版：逆向工作台卡片。
 *
 * 放在 MCP 设置页顶部。它做三件事：
 * 1. 一键拉起 MT 管理器 / SOMCP（未装则跳应用市场）。
 * 2. 一键"接管后端"：把预置的两个逆向 MCP 服务器（默认禁用）批量启用；再点则全部关闭。
 * 3. 展示两个后端当前是否已启用（enable 标记），提示用户去对应 App 开启服务。
 *
 * 不主动发探测请求——启用后由 MCP 子系统自动连接，连接状态在下方各服务器条目里显示。
 */
@Composable
fun ReverseWorkbenchCard(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    fun serverOf(id: Uuid) = settings.mcpServers.firstOrNull { it.id == id }

    // 单独开关某个 MCP
    fun setEnabled(serverId: Uuid, target: Boolean) {
        val updated = settings.mcpServers.map { server ->
            if (server.id == serverId) {
                server.clone(commonOptions = server.commonOptions.copy(enable = target))
            } else server
        }
        onUpdateSettings(settings.copy(mcpServers = updated))
    }

    // 端口可编辑：有人会在对应 App 里改监听端口，这里让用户手填，写回对应 MCP 服务器的 URL。
    fun updatePort(serverId: Uuid, newPort: String) {
        val port = newPort.filter { it.isDigit() }.take(5)
        if (port.isBlank()) return
        val updated = settings.mcpServers.map { server ->
            when {
                server.id != serverId -> server
                server is McpServerConfig.StreamableHTTPServer ->
                    server.copy(url = replacePort(server.url, port))
                server is McpServerConfig.SseTransportServer ->
                    server.copy(url = replacePort(server.url, port))
                else -> server
            }
        }
        onUpdateSettings(settings.copy(mcpServers = updated))
    }

    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var probing by remember { mutableStateOf(false) }

    // 玄星：一键探测本机端口并自动连接。App 间隔离读不到别人的配置，但可以主动去连本机端口——
    // 挨个探测各后端的常见端口，哪个通就把对应 MCP 的地址改成那个，实现小白一键连接。
    fun autoProbeAndConnect() {
        if (probing) return
        probing = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                // 每个后端的候选端口（默认端口在前 + 常见备用）
                val candidates = mapOf(
                    MT_APK_MCP_SERVER_ID to listOf(8787, 8788, 8080, 9999),
                    SOMCP_SO_MCP_SERVER_ID to listOf(8000, 8001, 8080, 9000),
                    PROXYPIN_MCP_SERVER_ID to listOf(9010, 9011, 9020),
                )
                val hits = mutableMapOf<Uuid, Int>()
                for ((id, ports) in candidates) {
                    for (p in ports) {
                        if (isPortOpen("127.0.0.1", p)) {
                            hits[id] = p
                            break
                        }
                    }
                }
                hits
            }
            if (found.isEmpty()) {
                toaster.show("没探测到可用后端。请先在 MT管理器/玄星逆核/ProxyPin 里启动它们的 MCP 服务再试。")
            } else {
                // 把探测到的端口写回对应 MCP 的 URL，并启用
                val updated = settings.mcpServers.map { server ->
                    val port = found[server.id]
                    if (port != null) {
                        val newServer = when (server) {
                            is McpServerConfig.StreamableHTTPServer -> server.copy(url = replacePort(server.url, port.toString()))
                            is McpServerConfig.SseTransportServer -> server.copy(url = replacePort(server.url, port.toString()))
                            else -> server
                        }
                        newServer.clone(commonOptions = newServer.commonOptions.copy(enable = true))
                    } else server
                }
                onUpdateSettings(settings.copy(mcpServers = updated))
                val names = found.keys.mapNotNull { id ->
                    when (id) {
                        MT_APK_MCP_SERVER_ID -> "MT管理器:${found[id]}"
                        SOMCP_SO_MCP_SERVER_ID -> "玄星逆核:${found[id]}"
                        PROXYPIN_MCP_SERVER_ID -> "ProxyPin:${found[id]}"
                        else -> null
                    }
                }.joinToString("、")
                toaster.show("已探测并连接：$names")
            }
            probing = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    HugeIcons.Link01,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "逆向工作台",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Text(
                text = "预置 3 个本机逆向 MCP 后端（玄星逆核已聚合全套逆向工具），每个可单独开关、单独改端口。" +
                    "不会填地址？先在对应 App 里启动 MCP 服务，再点下面「一键探测并连接」——自动找到端口并连上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 玄星：一键探测端口并自动连接（小白福音，不用手填地址）
            FilledTonalButton(
                onClick = { autoProbeAndConnect() },
                enabled = !probing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(HugeIcons.Link01, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = if (probing) "正在探测本机端口…" else "一键探测并连接（自动找端口）",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // 4 个后端各一行：独立开关 + 可编辑端口
            BackendRow(
                label = "MT 管理器 · APK 层",
                desc = "开包 / smali / AXML / 重签名 / 打包",
                enabled = serverOf(MT_APK_MCP_SERVER_ID)?.commonOptions?.enable == true,
                port = portOf(serverOf(MT_APK_MCP_SERVER_ID)?.serverUrl, "8787"),
                onToggle = { setEnabled(MT_APK_MCP_SERVER_ID, it) },
                onPortChange = { updatePort(MT_APK_MCP_SERVER_ID, it) },
            )
            HorizontalDivider()
            BackendRow(
                label = "玄星逆核 · 聚合逆向",
                desc = "反编译/脱壳/SO分析/模拟执行/回编签名/Frida 全套",
                enabled = serverOf(SOMCP_SO_MCP_SERVER_ID)?.commonOptions?.enable == true,
                port = portOf(serverOf(SOMCP_SO_MCP_SERVER_ID)?.serverUrl, "8000"),
                onToggle = { setEnabled(SOMCP_SO_MCP_SERVER_ID, it) },
                onPortChange = { updatePort(SOMCP_SO_MCP_SERVER_ID, it) },
            )
            HorizontalDivider()
            BackendRow(
                label = "ProxyPin · 抓包",
                desc = "HTTP/HTTPS 抓包与请求分析",
                enabled = serverOf(PROXYPIN_MCP_SERVER_ID)?.commonOptions?.enable == true,
                port = portOf(serverOf(PROXYPIN_MCP_SERVER_ID)?.serverUrl, "9010"),
                onToggle = { setEnabled(PROXYPIN_MCP_SERVER_ID, it) },
                onPortChange = { updatePort(PROXYPIN_MCP_SERVER_ID, it) },
            )

            // 一键拉起对应 App
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val pkg = if (context.isPackageInstalled(PKG_MT)) PKG_MT else PKG_MT_CANARY
                    context.launchOrOpenMarket(pkg)
                }) {
                    Text(if (isMtInstalled(context)) "打开 MT" else "装 MT")
                }
                OutlinedButton(onClick = {
                    context.launchOrOpenMarket(PKG_NIEHE)
                }) {
                    Text(if (context.isPackageInstalled(PKG_NIEHE)) "打开玄星逆核" else "装玄星逆核")
                }
                OutlinedButton(onClick = {
                    context.launchOrOpenMarket(PKG_PROXYPIN)
                }) {
                    Text(if (context.isPackageInstalled(PKG_PROXYPIN)) "打开 ProxyPin" else "装 ProxyPin")
                }
            }

            Text(
                text = "提示：启用后请到对应 App 里启动服务——MT 管理器在侧边栏开启「APK MCP」并保持后台，" +
                    "玄星逆核在首页点大启动按钮开服务（端口 8000）。都监听 127.0.0.1，仅本机可用。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackendRow(
    label: String,
    desc: String,
    enabled: Boolean,
    port: String,
    onToggle: (Boolean) -> Unit,
    onPortChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (enabled) "已启用" else "未启用",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("端口") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(92.dp)
                .padding(end = 8.dp),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

private fun isMtInstalled(context: android.content.Context): Boolean =
    context.isPackageInstalled(PKG_MT) || context.isPackageInstalled(PKG_MT_CANARY)

/** 玄星：探测本机某端口是否有服务在监听（TCP 连一下，300ms 超时，快速）。 */
private fun isPortOpen(host: String, port: Int): Boolean {
    return runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 300)
            true
        }
    }.getOrDefault(false)
}

/** 从 url（http://127.0.0.1:PORT/mcp）取端口，取不到用 fallback。 */
private fun portOf(url: String?, fallback: String): String {
    if (url.isNullOrBlank()) return fallback
    val m = Regex(":(\\d+)").find(url) ?: return fallback
    return m.groupValues[1].ifBlank { fallback }
}

/** 替换 url 里的端口段；无端口则在 host 后补上。 */
private fun replacePort(url: String, port: String): String {
    if (url.isBlank()) return "http://127.0.0.1:$port/mcp"
    val withPort = Regex("(://[^/:]+):\\d+")
    if (withPort.containsMatchIn(url)) {
        return withPort.replace(url) { m -> "${m.groupValues[1]}:$port" }
    }
    // 没有显式端口，在 host 后插入
    val hostOnly = Regex("(://[^/]+)")
    return hostOnly.replace(url) { m -> "${m.groupValues[1]}:$port" }
}
