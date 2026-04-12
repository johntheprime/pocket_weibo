package com.pocketweibo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WeiboOrange,
    onPrimary = White,
    primaryContainer = WeiboOrangePressed,
    onPrimaryContainer = White,
    secondary = Primary,
    onSecondary = White,
    background = Background,
    onBackground = Black,
    surface = Surface,
    onSurface = Black,
    surfaceVariant = TabBackground,
    onSurfaceVariant = GrayDark,
    outline = Divider,
    outlineVariant = GrayLight
)

@Composable
fun PocketWeiboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
