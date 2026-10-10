package me.rerere.rikkahub.ui.pages.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Cpu
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.compose.koinInject

/**
 * 玄星：首次启动引导。小白照着一步步配好模型/后端；会的人可随时「跳过」。
 * 完成或跳过后写入 onboardingCompleted=true，之后不再显示。
 */
@Composable
fun OnboardingPage() {
    val navController = LocalNavController.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    fun finish() {
        scope.launch {
            settingsStore.update { it.copy(onboardingCompleted = true) }
            navController.clearAndNavigate(Screen.Home)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            // 顶部图标
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFDB2777))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Sparkles,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            when (step) {
                0 -> StepContent(
                    icon = HugeIcons.Sparkles,
                    title = "欢迎使用玄星",
                    desc = "玄星是一个 AI 逆向工作台——你用大白话说需求，AI 自己调工具帮你脱壳、写 Frida、抓包、改 SO、分析 APK。\n\n下面用三步带你配好，会的话随时点右上「跳过」。",
                )
                1 -> StepContent(
                    icon = HugeIcons.Cpu,
                    title = "第 1 步 · 配一个 AI 模型",
                    desc = "玄星只是客户端，需要你自己的 AI 模型（OpenAI / Claude / Gemini 等接口，用自己的 API Key，很多平台有免费额度）。\n\n点下面按钮去「供应商」填 Key，然后在「模型」里选一个默认模型。",
                    primaryText = "去配置模型",
                    onPrimary = { navController.navigate(Screen.SettingProvider) },
                )
                2 -> StepContent(
                    icon = HugeIcons.McpServer,
                    title = "第 2 步 · 连接逆向后端（可选）",
                    desc = "想让 AI 真正动手拆 APK/改 SO，需要装对应后端 App：MT 管理器 / SOMCP / 算法助手 / ProxyPin（按需装）。\n\n装好并在里面启动服务后，去「MCP 设置」点「一键探测并连接」自动连上。只用 AI 聊天/写脚本的话这步可跳过。",
                    primaryText = "去 MCP 设置",
                    onPrimary = { navController.navigate(Screen.SettingMcp) },
                )
                3 -> StepContent(
                    icon = HugeIcons.Tick01,
                    title = "全部就绪！",
                    desc = "默认已进入「逆向工作台」助手。直接说需求就行，比如：\n\n· \"分析这个 APK 用了什么加固\"\n· \"帮我写个 Frida 脚本过 SSL Pinning\"\n· \"这个 sign 参数怎么算的\"\n\n首页还有 6 个一键任务模板，点一下直接开干。",
                    primaryText = "开始使用",
                    onPrimary = { finish() },
                )
            }

            Spacer(Modifier.height(24.dp))

            // 步骤指示点
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(totalSteps) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 底部导航按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text("上一步") }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                if (step < totalSteps - 1) {
                    Button(onClick = { step++ }) { Text("下一步") }
                } else {
                    Button(onClick = { finish() }) { Text("完成") }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { finish() }) {
                Text("跳过引导", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    primaryText: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = desc,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
    if (primaryText != null && onPrimary != null) {
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(primaryText)
        }
    }
}
