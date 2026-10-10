package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import org.koin.compose.koinInject

/**
 * 玄星：AI 生成悬浮球。
 * 后台/任意页面只要有会话正在生成，就在屏幕角落显示一个玄星图标悬浮球 + 转圈，
 * 让用户知道 AI 还在干活（尤其配合消息保活后台生成）。应用内悬浮，无需系统权限。
 * 由 Settings.enableAiFloatingWindow 开关控制。
 */
@Composable
fun AiGeneratingFloatingWindow() {
    val settingsStore = koinInject<SettingsStore>()
    val chatService = koinInject<ChatService>()

    val settings by settingsStore.settingsFlow.collectAsState(initial = null)
    val enabled = settings?.enableAiFloatingWindow == true

    // 是否有任意会话正在生成
    val generatingCount by remember {
        chatService.getConversationJobs().map { jobs ->
            jobs.values.count { it?.isActive == true }
        }
    }.collectAsState(initial = 0)

    val visible = enabled && generatingCount > 0

    FloatingWindow(
        tag = "ai_generating",
        visibility = visible
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "玄星生成中",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
