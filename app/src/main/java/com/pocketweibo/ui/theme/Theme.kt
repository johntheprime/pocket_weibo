package com.pocketweibo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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

private val DarkColorScheme = darkColorScheme(
    primary = WeiboOrange,
    onPrimary = White,
    primaryContainer = Color(0xFFB5651D),
    onPrimaryContainer = White,
    secondary = Color(0xFF7986CB),
    onSecondary = White,
    background = Color(0xFF121212),
    onBackground = White,
    surface = Color(0xFF1E1E1E),
    onSurface = White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF303030)
)

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun PocketWeiboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
