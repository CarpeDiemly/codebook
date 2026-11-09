package com.carpediemly.codebook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 品牌主色：偏深蓝，中性、克制，不抢密码内容。 */
private val BrandBlue = Color(0xFF1F4E8C)
private val BrandBlueLight = Color(0xFFA8C7FA)
private val BrandTeal = Color(0xFF00696E)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF002020),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1A1B1F),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF0B3D74),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF6FD5D8),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F52),
    onSecondaryContainer = Color(0xFFCCE8E9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surface = Color(0xFF121317),
    onSurface = Color(0xFFE3E2E6),
)

/**
 * 全局主题。API 31+ 默认跟随系统壁纸取色；旧版本使用品牌色。
 */
@Composable
fun CodeBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
