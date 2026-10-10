package me.rerere.rikkahub.ui.components.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import androidx.compose.material3.Text
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController

/**
 * 池鸳魔改版 · 主导航外壳
 *
 * 在 NavDisplay 外面包一层 Scaffold，仅在 4 个 tab 根路由
 * (Home / History / Skills / Setting) 显示底部导航栏，
 * 其他页面（聊天详情、各种设置子页）保持全屏、不显示底栏。
 */
private data class TabItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    TabItem(Screen.Home, "首页", HugeIcons.Sparkles),
    TabItem(Screen.History, "对话", HugeIcons.Message01),
    TabItem(Screen.Skills, "技能", HugeIcons.Package),
    TabItem(Screen.Setting, "设置", HugeIcons.Settings03),
)

@Composable
fun MainScaffold(
    backStack: List<NavKey>,
    content: @Composable (Modifier) -> Unit,
) {
    val navController = LocalNavController.current
    val current = backStack.lastOrNull()

    // 判断当前是否停在某个 tab 根路由
    val currentTab = remember(current) {
        TABS.firstOrNull { it.screen == current }
    }
    val showBar = currentTab != null

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = tab.screen == current
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.clearAndNavigate(tab.screen)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // 只有显示底栏时才把内容上移（留出底栏空间）；
        // 全屏页面（如聊天）不加 padding，避免与其自身的输入框/insets 叠加。
        val contentModifier = if (showBar) {
            Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        } else {
            Modifier
        }
        content(contentModifier)
    }
}
