package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.clearMtMcpCache
import me.rerere.rikkahub.utils.formatBytes
import me.rerere.rikkahub.utils.getMtMcpCacheSize
import me.rerere.rikkahub.utils.hasAllFilesAccess
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.requestAllFilesAccess
import org.koin.androidx.compose.koinViewModel

/**
 * 玄星：高级功能设置页——集中放二改新增的开关。
 */
@Composable
fun SettingAdvancedPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 玄星：MT 分析缓存占用与清理确认
    var mtCacheSize by remember { mutableStateOf(-1L) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // 进页面时异步统计一次占用
    androidx.compose.runtime.LaunchedEffect(Unit) {
        mtCacheSize = withContext(Dispatchers.IO) { runCatching { getMtMcpCacheSize() }.getOrDefault(0L) }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清理 MT 分析缓存") },
            text = {
                Text(
                    "将删除 MT 管理器 MCP 解包产生的临时文件（Android/data/bin.mt.plus[.canary]/mcp）。" +
                        "这些是分析 APK 时的中间产物，删除不影响 MT 管理器本身，下次分析会自动重建。\n\n" +
                        "当前占用：${if (mtCacheSize < 0) "统计中…" else formatBytes(mtCacheSize)}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { runCatching { clearMtMcpCache() }.getOrDefault(0L) }
                        mtCacheSize = withContext(Dispatchers.IO) { runCatching { getMtMcpCacheSize() }.getOrDefault(0L) }
                        if (freed > 0) {
                            toaster.show("已清理 ${formatBytes(freed)}")
                        } else {
                            toaster.show("没有可清理的缓存，或没有文件访问权限（请先开启\"全部文件访问\"）")
                        }
                    }
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("玄星高级功能") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    // 消息保活
                    item(
                        headlineContent = { Text("消息保活") },
                        supportingContent = { Text("生成时占用前台服务，防止后台/熄屏被系统杀掉，AI 对话继续跑完。") },
                        trailingContent = {
                            Switch(
                                checked = settings.enableChatKeepAlive,
                                onCheckedChange = { vm.updateSettings(settings.copy(enableChatKeepAlive = it)) }
                            )
                        },
                    )
                    // AI 悬浮窗
                    item(
                        headlineContent = { Text("AI 悬浮窗") },
                        supportingContent = { Text("后台生成时显示悬浮球，切到其他 App/桌面也能看到 AI 还在干活。需授予「显示在其他应用上层」权限。") },
                        trailingContent = {
                            Switch(
                                checked = settings.enableAiFloatingWindow,
                                onCheckedChange = { on ->
                                    vm.updateSettings(settings.copy(enableAiFloatingWindow = on))
                                    // 开启且未授悬浮窗权限时，引导去系统授权页（否则只能应用内显示）
                                    if (on && !android.provider.Settings.canDrawOverlays(context)) {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                }
                            )
                        },
                    )
                    // 持续工作
                    item(
                        headlineContent = { Text("持续工作") },
                        supportingContent = { Text("AI 停下后若任务没干完，自动追加\"继续\"接着做，最多 ${settings.continuousWorkMaxRounds} 轮后停止。") },
                        trailingContent = {
                            Switch(
                                checked = settings.continuousWorkEnabled,
                                onCheckedChange = { vm.updateSettings(settings.copy(continuousWorkEnabled = it)) }
                            )
                        },
                    )
                    // 自动压缩会话（点进去可自定义 4 个参数）
                    item(
                        headlineContent = { Text("自动压缩会话") },
                        supportingContent = {
                            Text(
                                if (settings.autoCompressEnabled)
                                    "已开启 · 约 ${settings.autoCompressThresholdTokens} tokens 触发，保留最近 ${settings.autoCompressKeepRecent} 条。点击自定义。"
                                else "上下文快超限时自动把历史压成摘要，省钱防超限。点击配置。"
                            )
                        },
                        onClick = { navController.navigate(me.rerere.rikkahub.Screen.SettingAutoCompress) },
                    )
                    // 全部文件访问
                    item(
                        headlineContent = { Text("全部文件访问") },
                        supportingContent = {
                            Text(
                                if (context.hasAllFilesAccess()) "已授权，可读写 /sdcard、/storage 下的文件。"
                                else "点击授权全部文件访问权限（逆向常需读写外部存储的 APK/SO）。"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = context.hasAllFilesAccess(),
                                onCheckedChange = { if (it) context.requestAllFilesAccess() }
                            )
                        },
                        modifier = Modifier,
                    )
                    // 一键清理 MT 分析缓存
                    item(
                        headlineContent = { Text("清理 MT 分析缓存") },
                        supportingContent = {
                            Text(
                                if (mtCacheSize < 0) "统计占用中…"
                                else "MT 开包解出的临时文件已占用 ${formatBytes(mtCacheSize)}，点此清理（不影响 MT 本身）。"
                            )
                        },
                        onClick = {
                            if (!context.hasAllFilesAccess()) {
                                context.requestAllFilesAccess()
                            } else {
                                showClearConfirm = true
                            }
                        },
                    )
                }
            }
        }
    }
}
