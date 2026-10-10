package me.rerere.rikkahub.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.history.HistoryVM
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

// 玄星首页跟随主题配色（深色模式全深、浅色模式全浅，和底栏/其他页统一，避免割裂）。
// 用 @Composable getter 从 MaterialTheme 取色，保持原有 XxXxx 名称不改调用点。
private val XxBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val XxCard: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val XxText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val XxTextDim: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val XxAccent: Color @Composable get() = MaterialTheme.colorScheme.primary

/**
 * 玄星 · 首页仪表盘（方案B）
 * 顶部欢迎区 + 快捷入口卡片 + 最近会话列表，替代原版樱花抽屉首屏。
 * 固定深色科技风，观感与原型图三一致。
 */
@Composable
fun HomeDashboardPage(vm: HistoryVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val conversations by vm.conversations.collectAsStateWithLifecycle()

    val assistant = settings.getCurrentAssistant()
    val skillCount = assistant.enabledSkills.size

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = XxBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ===== 欢迎区 Hero =====
            item {
                HeroCard(skillCount = skillCount)
            }

            // ===== 快捷入口卡片 =====
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = HugeIcons.Package,
                        tint = Color(0xFF4F46E5),
                        title = "分析 APK",
                        desc = "脱壳·反编译",
                        onClick = {
                            navigateToChatPage(
                                navController,
                                initText = "帮我分析这个 APK 用了什么加固，并给出脱壳方案".base64Encode()
                            )
                        }
                    )
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = HugeIcons.AiMagic,
                        tint = Color(0xFFDB2777),
                        title = "Frida",
                        desc = "动态 Hook",
                        onClick = {
                            navigateToChatPage(
                                navController,
                                initText = "帮我生成一个 Frida 脚本绕过 SSL Pinning".base64Encode()
                            )
                        }
                    )
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = HugeIcons.Brain02,
                        tint = Color(0xFF10B981),
                        title = "改 SO",
                        desc = "汇编 Patch",
                        onClick = {
                            navigateToChatPage(
                                navController,
                                initText = "帮我用 SOMCP 分析并 patch 这个 so 的关键函数".base64Encode()
                            )
                        }
                    )
                }
            }

            // ===== 最近会话标题 =====
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近会话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = XxText
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "查看全部",
                        style = MaterialTheme.typography.labelMedium,
                        color = XxAccent,
                        modifier = Modifier.clickable {
                            navController.navigate(Screen.History)
                        }
                    )
                }
            }

            // ===== 会话列表 =====
            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "还没有会话，点上方卡片开始一个逆向任务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = XxTextDim
                        )
                    }
                }
            } else {
                items(conversations.take(12), key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        onClick = { navigateToChatPage(navController, conv.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(skillCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4F46E5),
                        Color(0xFF7C3AED),
                        Color(0xFFDB2777)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = HugeIcons.Sparkles,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "池鸳 · AI 逆向工作台",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "今天要拆哪个 App？",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$skillCount 个技能已启用 · MCP 就绪",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun QuickCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(XxCard)
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = XxText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.labelSmall,
            color = XxTextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val title = conversation.title.ifBlank { "新对话" }
    val preview = conversation.messageNodes
        .asReversed()
        .firstNotNullOfOrNull { node -> node.messages.getOrNull(node.selectIndex) ?: node.messages.lastOrNull() }
        ?.toText()
        ?.trim()
        ?.replace('\n', ' ')
        ?.take(40)
        ?.ifBlank { "（空会话）" }
        ?: "（空会话）"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(XxCard)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF4F46E5), Color(0xFF818CF8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = HugeIcons.PencilEdit01,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = XxText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = XxTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
