package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * 玄星：自定义自动压缩配置页。
 * 4 个可调参数：触发 token 估算值 / 最低消息数量 / 保留最近消息 / 摘要目标 tokens。
 * 开启后在“发送前”和“回复后”都会按此策略自动压缩历史，防止上下文超限。
 */
@Composable
fun SettingAutoCompressPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 本地草稿：进页面时用当前设置初始化，改完点保存才写回
    var enabled by remember(settings.autoCompressEnabled) { mutableStateOf(settings.autoCompressEnabled) }
    var threshold by remember(settings.autoCompressThresholdTokens) { mutableStateOf(settings.autoCompressThresholdTokens.toString()) }
    var minMessages by remember(settings.autoCompressMinMessages) { mutableStateOf(settings.autoCompressMinMessages.toString()) }
    var keepRecent by remember(settings.autoCompressKeepRecent) { mutableStateOf(settings.autoCompressKeepRecent.toString()) }
    var targetTokens by remember(settings.autoCompressTargetTokens) { mutableStateOf(settings.autoCompressTargetTokens.toString()) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("自定义自动压缩") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup {
                item(
                    headlineContent = { Text("启用自动压缩") },
                    supportingContent = { Text("在下一次生成回复前自动应用此策略。") },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )
                    },
                )
            }

            FormItem(
                label = { Text("触发 token 估算值") },
                description = { Text("估算上下文达到该值时压缩（1,000 ~ 2,000,000）。") },
            ) {
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
            }

            FormItem(
                label = { Text("最低消息数量") },
                description = { Text("少于该数量的短会话不压缩（2 ~ 500）。") },
            ) {
                OutlinedTextField(
                    value = minMessages,
                    onValueChange = { minMessages = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
            }

            FormItem(
                label = { Text("保留最近消息") },
                description = { Text("这些消息保留原文；必须小于最低消息数量。") },
            ) {
                OutlinedTextField(
                    value = keepRecent,
                    onValueChange = { keepRecent = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
            }

            FormItem(
                label = { Text("摘要目标 tokens") },
                description = { Text("期望摘要长度（128 到触发值的一半）。") },
            ) {
                OutlinedTextField(
                    value = targetTokens,
                    onValueChange = { targetTokens = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // 校验并夹取到合法范围
                        val th = (threshold.toIntOrNull() ?: 200_000).coerceIn(1_000, 2_000_000)
                        val mm = (minMessages.toIntOrNull() ?: 20).coerceIn(2, 500)
                        var kr = (keepRecent.toIntOrNull() ?: 5).coerceIn(0, mm - 1)
                        if (kr >= mm) kr = mm - 1
                        val tt = (targetTokens.toIntOrNull() ?: 4000).coerceIn(128, th / 2)
                        vm.updateSettings(
                            settings.copy(
                                autoCompressEnabled = enabled,
                                autoCompressThresholdTokens = th,
                                autoCompressMinMessages = mm,
                                autoCompressKeepRecent = kr,
                                autoCompressTargetTokens = tt,
                            )
                        )
                        // 回写显示（夹取后的值）
                        threshold = th.toString(); minMessages = mm.toString()
                        keepRecent = kr.toString(); targetTokens = tt.toString()
                        toaster.show("已保存")
                    },
                ) { Text("保存") }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        enabled = false
                        threshold = "200000"; minMessages = "20"; keepRecent = "5"; targetTokens = "4000"
                    },
                ) { Text("恢复默认") }
            }

            Text(
                text = "说明：开启后，聊天历史接近“触发 token 估算值”时会自动把较早的消息压成摘要，只保留最近几条原文，从而避免超出模型上下文上限。逆向长任务建议开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
