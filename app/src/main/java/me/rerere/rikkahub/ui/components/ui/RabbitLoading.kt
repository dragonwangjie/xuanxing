package me.rerere.rikkahub.ui.components.ui

import android.widget.ImageView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalSettings

/**
 * 玄星：加载指示器。
 * useAppIconStyleLoadingIndicator=true 时显示玄星图标（旋转 + 轻微呼吸缩放）；否则用普通圆形指示器。
 * （原 RikkaHub 的兔子吉祥物已替换为玄星图标。）
 * 注意：ic_launcher 在 API26+ 是自适应图标 XML，painterResource 不支持，必须用 ImageView 渲染。
 */
@Composable
fun RabbitLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator = LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator

    if (useAppIconStyleLoadingIndicator) {
        val transition = rememberInfiniteTransition(label = "xuanxing-loading")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "angle",
        )
        val pulse by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        // 用 ImageView 渲染自适应图标（ImageView 支持 adaptive-icon），外层用 Compose 做旋转+呼吸动画
        AndroidView(
            modifier = modifier
                .rotate(angle)
                .scale(pulse),
            factory = { context ->
                ImageView(context).apply {
                    setImageResource(R.mipmap.ic_launcher)
                }
            },
        )
    } else {
        ContainedLoadingIndicator(
            modifier = modifier,
        )
    }
}
