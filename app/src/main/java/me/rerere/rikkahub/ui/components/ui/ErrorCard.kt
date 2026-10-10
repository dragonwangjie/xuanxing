package me.rerere.rikkahub.ui.components.ui

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatErrorSolution
import me.rerere.rikkahub.ui.context.LocalNavController
import kotlin.uuid.Uuid

@Composable
fun ErrorCardsDisplay(
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = errors.isNotEmpty(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 清除全部按钮（当有多个错误时显示）
            if (errors.size > 1) {
                Surface(
                    onClick = onClearAllErrors,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.chat_page_clear_all_errors),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // 错误卡片列表
            errors.forEach { error ->
                ErrorCard(
                    error = error,
                    onDismiss = { onDismissError(error.id) },
                )
            }
        }
    }
}

@Composable
fun ErrorCard(
    error: ChatError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val checkTitleModelSettings = stringResource(R.string.chat_page_check_title_model_settings)
    val linkColor = MaterialTheme.colorScheme.primary

    // 5 秒后自动消失
    LaunchedEffect(error.id) {
        delay(5000)
        onDismiss()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (error.title != null) {
                    Text(
                        text = error.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 玄星：常见报错翻译成中文人话（小白友好）；无匹配时直接显示原文
                val friendly = translateErrorMessage(error.error)
                Text(
                    text = friendly ?: (error.error.message ?: "Unknown error"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    overflow = TextOverflow.Ellipsis,
                )
                // 有中文翻译时，把英文原文降级为小字附在下面，方便老手核对
                if (friendly != null) {
                    Text(
                        text = error.error.message ?: "Unknown error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 玄星：错误来源判断（帮用户分清是模型/API 的锅还是软件本身的问题）
                Text(
                    text = classifyErrorSource(error.error),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
                if (error.solution == ChatErrorSolution.CheckTitleModelSettings) {
                    Text(
                        text = buildAnnotatedString {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "check_title_model_settings",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        )
                                    ),
                                    linkInteractionListener = {
                                        navController.navigate(Screen.SettingModels)
                                    },
                                )
                            ) {
                                append(checkTitleModelSettings)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(
                                clipData = ClipData.newPlainText("Error", error.error.message ?: "Unknown error")
                            )
                        )
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Copy01,
                    contentDescription = "Copy error message",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = stringResource(R.string.chat_page_dismiss_error),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 玄星：判断错误来源，给用户一句人话结论——到底是"模型/接口的问题"还是"软件/网络的问题"，
 * 免得一报错就以为是玄星的 bug 来骂（很多其实是 API 限流/余额/被拦截）。
 */
private fun classifyErrorSource(e: Throwable): String {
    val msg = (e.message ?: "").lowercase()
    val cls = e.javaClass.simpleName
    return when {
        // API / 模型服务端问题
        msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid api key") ->
            "🔑 来源：API Key 无效或未授权 —— 去「设置→模型/供应商」检查 Key。"
        msg.contains("余额") || msg.contains("balance") || msg.contains("quota") || msg.contains("insufficient") ||
            msg.contains("exhausted") || msg.contains("all available accounts") || msg.contains("no available") ||
            msg.contains("credit") || msg.contains("欠费") || msg.contains("额度") ->
            "💰 来源：API 账户余额/额度用尽 —— 是模型服务商/你的 Key 那边的问题，不是软件 bug。请充值或换一个可用的模型/Key。"
        msg.contains("429") || msg.contains("rate limit") || msg.contains("too many") || msg.contains("overloaded") ->
            "🚦 来源：API 限流/服务繁忙 —— 模型服务商临时限制，稍后重试即可。"
        msg.contains("flagged") || msg.contains("content") && msg.contains("policy") ->
            "🛡 来源：内容被模型服务商安全策略拦截 —— 换个说法或换模型。"
        msg.contains("400") || msg.contains("bad request") || msg.contains("invalid") ->
            "⚙ 来源：请求参数问题（模型不支持某参数/格式）—— 多为模型配置，试试换模型或关掉高级参数。"
        msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("gateway") ->
            "🌐 来源：模型服务商服务器出错（5xx）—— 是对方服务的问题，稍后重试。"
        // 网络问题
        msg.contains("timeout") || msg.contains("timed out") || cls.contains("Timeout") ->
            "⏱ 来源：网络超时 —— 检查网络/代理，或稍后重试（玄星会自动重试几次）。"
        msg.contains("unable to resolve host") || msg.contains("unknownhost") || cls.contains("UnknownHost") ->
            "📡 来源：连不上服务器（DNS/网络）—— 检查网络、代理或 baseUrl 是否正确。"
        msg.contains("connection") || msg.contains("connect") || cls.contains("Connect") || cls.contains("IO") ->
            "📶 来源：网络连接问题 —— 检查网络/代理后重试。"
        // 其余归为软件侧
        else ->
            "🐞 来源：可能是软件内部问题 —— 若反复出现，复制错误信息到反馈渠道反馈。"
    }
}

/**
 * 玄星：把常见的英文报错翻译成中文人话 + 解决建议。命中返回中文，未命中返回 null（调用方回退显示英文原文）。
 * 只做关键词匹配，离线即时零成本，不依赖网络/模型。
 */
private fun translateErrorMessage(e: Throwable): String? {
    val msg = (e.message ?: "").lowercase()
    val cls = e.javaClass.simpleName
    return when {
        msg.contains("maximum context length") || msg.contains("context length") ||
            msg.contains("context window") || (msg.contains("token") && msg.contains("exceed")) ->
            "对话太长，超过了模型的上下文上限。请开新对话，或在「高级功能」开启自动压缩历史。"
        msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid api key") ||
            msg.contains("invalid_api_key") ->
            "API Key 错误或已失效。去「设置→模型/供应商」检查 Key 是否填对。"
        msg.contains("余额") || msg.contains("balance") || msg.contains("quota") || msg.contains("insufficient") ||
            msg.contains("exhausted") || msg.contains("all available accounts") || msg.contains("no available") ||
            msg.contains("credit") || msg.contains("欠费") || msg.contains("额度") ->
            "账户额度用完或欠费了。这是模型服务商/你 Key 那边的问题，请充值或换一个可用的模型/Key。"
        msg.contains("429") || msg.contains("rate limit") || msg.contains("too many") || msg.contains("overloaded") ->
            "请求太频繁或服务繁忙，被模型服务商限流了。稍等一会儿再试。"
        msg.contains("model") && (msg.contains("not found") || msg.contains("does not exist") || msg.contains("not exist")) ->
            "模型名填错了或该中转不提供这个模型。检查模型 ID 是否正确。"
        msg.contains("404") ->
            "接口地址或模型找不到（404）。检查 baseUrl 和模型 ID 是否正确。"
        msg.contains("flagged") || (msg.contains("content") && msg.contains("policy")) ->
            "内容被模型服务商的安全策略拦截了。换个说法或换个模型再试。"
        msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ||
            msg.contains("bad gateway") || msg.contains("gateway") ->
            "模型服务商的服务器出错了（5xx）。是对方服务的问题，稍后重试。"
        msg.contains("timeout") || msg.contains("timed out") || cls.contains("Timeout") ->
            "网络超时了。检查网络或代理后重试（玄星会自动重试几次）。"
        msg.contains("unable to resolve host") || msg.contains("unknownhost") || cls.contains("UnknownHost") ->
            "连不上服务器（DNS/网络问题）。检查网络、代理，或 baseUrl 是否填对。"
        msg.contains("connection") || msg.contains("connect") || cls.contains("Connect") ->
            "网络连接失败。检查网络/代理后重试。"
        else -> null
    }
}
