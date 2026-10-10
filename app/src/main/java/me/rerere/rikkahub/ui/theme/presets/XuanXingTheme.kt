package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.theme.PresetTheme

/**
 * 玄星主题 · 紫色科技风（方案B）
 * 深色：深蓝灰底 + 紫色主色，接近首页仪表盘原型。
 * 浅色：淡紫亮底。
 */
val XuanXingThemePreset by lazy {
    PresetTheme(
        id = "xuanxing",
        name = {
            Text("玄星")
        },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

//region 玄星 Light（淡紫）
private val primaryLight = Color(0xFF5B4BC4)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFE4DEFF)
private val onPrimaryContainerLight = Color(0xFF43348C)
private val secondaryLight = Color(0xFF9333A8)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFFAD8FF)
private val onSecondaryContainerLight = Color(0xFF75157F)
private val tertiaryLight = Color(0xFFC2185B)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFFFD9E2)
private val onTertiaryContainerLight = Color(0xFF8E1149)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF93000A)
private val backgroundLight = Color(0xFFFBF8FF)
private val onBackgroundLight = Color(0xFF1B1B21)
private val surfaceLight = Color(0xFFFBF8FF)
private val onSurfaceLight = Color(0xFF1B1B21)
private val surfaceVariantLight = Color(0xFFE4E0EC)
private val onSurfaceVariantLight = Color(0xFF47464F)
private val outlineLight = Color(0xFF787680)
private val outlineVariantLight = Color(0xFFC8C5D0)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF303036)
private val inverseOnSurfaceLight = Color(0xFFF2EFF7)
private val inversePrimaryLight = Color(0xFFC7BEFF)
private val surfaceDimLight = Color(0xFFDBD9E0)
private val surfaceBrightLight = Color(0xFFFBF8FF)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFF5F2FA)
private val surfaceContainerLight = Color(0xFFEFEDF4)
private val surfaceContainerHighLight = Color(0xFFEAE7EF)
private val surfaceContainerHighestLight = Color(0xFFE4E1E9)

//region 玄星 Dark（深蓝紫黑，对齐原型 #0F1117 / #181B26）
private val primaryDark = Color(0xFFA78BFA)
private val onPrimaryDark = Color(0xFF2A1B66)
private val primaryContainerDark = Color(0xFF4F46E5)
private val onPrimaryContainerDark = Color(0xFFE4DEFF)
private val secondaryDark = Color(0xFFF0ABFC)
private val onSecondaryDark = Color(0xFF54176B)
private val secondaryContainerDark = Color(0xFF7C3AED)
private val onSecondaryContainerDark = Color(0xFFFAD8FF)
private val tertiaryDark = Color(0xFFFFB1C8)
private val onTertiaryDark = Color(0xFF5E1133)
private val tertiaryContainerDark = Color(0xFFDB2777)
private val onTertiaryContainerDark = Color(0xFFFFD9E2)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF0F1117)
private val onBackgroundDark = Color(0xFFE8EAED)
private val surfaceDark = Color(0xFF0F1117)
private val onSurfaceDark = Color(0xFFE8EAED)
private val surfaceVariantDark = Color(0xFF2E3345)
private val onSurfaceVariantDark = Color(0xFFC8C5D0)
private val outlineDark = Color(0xFF5C6178)
private val outlineVariantDark = Color(0xFF2E3345)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFE8EAED)
private val inverseOnSurfaceDark = Color(0xFF303036)
private val inversePrimaryDark = Color(0xFF5B4BC4)
private val surfaceDimDark = Color(0xFF0F1117)
private val surfaceBrightDark = Color(0xFF35353B)
private val surfaceContainerLowestDark = Color(0xFF0A0C10)
private val surfaceContainerLowDark = Color(0xFF14171F)
private val surfaceContainerDark = Color(0xFF181B26)
private val surfaceContainerHighDark = Color(0xFF232735)
private val surfaceContainerHighestDark = Color(0xFF2E3345)

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)
