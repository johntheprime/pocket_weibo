package com.pocketweibo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit

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

/** UI text scale (1f = system default) applied through [LocalDensity] to every `sp` value. */
val LocalUiFontScale = compositionLocalOf { 1f }

/** Post-body/comment text scale (1f = system default), independent of [LocalUiFontScale]. */
val LocalPostFontScale = compositionLocalOf { 1f }

/**
 * Post-body size relative to the UI scale: the post setting is absolute, so divide out the
 * UI multiplier that [LocalDensity] already applies to every `sp` value.
 */
@Composable
private fun postFontFactor(): Float = LocalPostFontScale.current / LocalUiFontScale.current

/** Scales an explicit post-body/comment `sp` size by the post font-size setting. */
@Composable
fun postTextSize(size: TextUnit): TextUnit {
    val factor = postFontFactor()
    return if (factor == 1f || size == TextUnit.Unspecified) size else size * factor
}

/** Scales a post-body [TextStyle] (including its line height) by the post font-size setting. */
@Composable
fun postTextStyle(style: TextStyle): TextStyle {
    val factor = postFontFactor()
    if (factor == 1f) return style
    return style.copy(
        fontSize = postTextSize(style.fontSize),
        lineHeight = postTextSize(style.lineHeight)
    )
}

@Composable
fun PocketWeiboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** FP-P3: wallpaper-based colors (Android 12+); ignored on older devices. */
    dynamicColor: Boolean = false,
    uiFontScale: Float = 1f,
    postFontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * uiFontScale)

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalUiFontScale provides uiFontScale,
        LocalPostFontScale provides postFontScale,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
