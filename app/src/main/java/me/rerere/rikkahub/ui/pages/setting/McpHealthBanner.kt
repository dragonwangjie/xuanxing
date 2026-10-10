package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.uuid.Uuid

/**
 * 池鸳魔改版：MCP 健康看板（精简版，内嵌在 MCP 设置页顶部）。
 *
 * 汇总所有已启用 MCP 服务器的实时连接状态，一眼看出：几个在线、几个连接中、几个出错。
 * 逆向时最烦"AI 说工具用不了却不知道是哪个后端掉了"，这个横幅直接给出全局健康度。
 * 数据来自 McpManager.syncingStatus，不额外发请求。
 */
@Composable
fun McpHealthBanner(
    configs: List<McpServerConfig>,
    status: Map<Uuid, McpStatus>,
    modifier: Modifier = Modifier,
) {
    // 只统计启用了的服务器（未启用的不连接，不计入健康度）
    val enabled = configs.filter { it.commonOptions.enable }
    if (enabled.isEmpty()) return

    var online = 0
    var connecting = 0
    var error = 0
    var needsAuth = 0
    enabled.forEach { cfg ->
        when (status[cfg.id]) {
            is McpStatus.Connected -> online++
            is McpStatus.Connecting, is McpStatus.Reconnecting -> connecting++
            is McpStatus.Error -> error++
            is McpStatus.NeedsAuthorization, is McpStatus.Authorizing -> needsAuth++
            else -> {} // Idle/null：尚未开始，不计入
        }
    }

    val allGood = error == 0 && needsAuth == 0 && connecting == 0 && online == enabled.size
    val containerColor = when {
        error > 0 || needsAuth > 0 -> MaterialTheme.colorScheme.errorContainer
        connecting > 0 -> MaterialTheme.colorScheme.surfaceVariant
        allGood -> MaterialTheme.colorScheme.primaryContainer
        else -> CustomColors.listItemColors.containerColor
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MCP 后端状态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "  （已启用 ${enabled.size} 个）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(color = Color(0xFF2E7D32), label = "在线", count = online)
                if (connecting > 0) StatusPill(color = Color(0xFFF9A825), label = "连接中", count = connecting)
                if (error > 0) StatusPill(color = MaterialTheme.colorScheme.error, label = "离线/错误", count = error)
                if (needsAuth > 0) StatusPill(color = Color(0xFF6A1B9A), label = "待授权", count = needsAuth)
            }
            val hint = when {
                error > 0 -> "有后端离线：请到对应 App（MT 管理器/SOMCP 等）确认服务已启动并保持后台。"
                needsAuth > 0 -> "有后端需要授权：点开对应服务器完成 OAuth 授权。"
                connecting > 0 -> "正在连接…下拉可刷新。"
                allGood -> "全部在线，工具可用。"
                else -> "下拉刷新以重新连接。"
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
