package com.bytegif.glassbeat.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.skia.Image

/**
 * Exact GlassBeat Color & Glassmorphic Surface Palette ported from colors.xml & themes.xml.
 */
@Immutable
data class GlassColorPalette(
    val isDark: Boolean,
    val background: Color,
    val backgroundGradientEnd: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val divider: Color,
    val miniPlayerBg: Color,
    val danger: Color
)

val DarkGlassPalette = GlassColorPalette(
    isDark = true,
    background = Color(0xFF050505),
    backgroundGradientEnd = Color(0xFF0F1115),
    surface = Color(0xFF0D0E12),
    surfaceVariant = Color(0xFF151518),
    glassSurface = Color(0xA616181E),
    glassSurfaceElevated = Color(0xD91E2028),
    glassBorder = Color(0x2EFFFFFF),
    glassHighlight = Color(0x18FFFFFF),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    accent = Color(0xFF64D2FF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFA0A4B0),
    divider = Color(0x1FFFFFFF),
    miniPlayerBg = Color(0xBF15161B),
    danger = Color(0xFFFF453A)
)

val LightGlassPalette = GlassColorPalette(
    isDark = false,
    background = Color(0xFFF5F6F8),
    backgroundGradientEnd = Color(0xFFE8ECF2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEDF0),
    glassSurface = Color(0xC4FFFFFF),
    glassSurfaceElevated = Color(0xEBFFFFFF),
    glassBorder = Color(0x24000000),
    glassHighlight = Color(0x75FFFFFF),
    primary = Color(0xFF1C1B1F),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF007AFF),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF5F6368),
    divider = Color(0x18000000),
    miniPlayerBg = Color(0xD9F0F1F4),
    danger = Color(0xFFFF3B30)
)

val LocalGlassColors = staticCompositionLocalOf { DarkGlassPalette }

object GlassTheme {
    val colors: GlassColorPalette
        @Composable
        get() = LocalGlassColors.current
}

/**
 * Tracks active glass modals so the underlying main window and/or Now Playing screen
 * automatically applies a real Gaussian blur underneath any open dialog/sheet.
 */
object GlassModalState {
    var activeModalCount by mutableIntStateOf(0)
    var activeNowPlayingModalCount by mutableIntStateOf(0)
}

@Composable
fun rememberMainBlurRadius(extraCondition: Boolean = false): Dp {
    val shouldBlur = GlassModalState.activeModalCount > 0 || extraCondition
    val blurRadius by animateDpAsState(
        targetValue = if (shouldBlur) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "main_blur"
    )
    return blurRadius
}

@Composable
fun rememberNowPlayingBlurRadius(extraCondition: Boolean = false): Dp {
    val shouldBlur = GlassModalState.activeNowPlayingModalCount > 0 || extraCondition
    val blurRadius by animateDpAsState(
        targetValue = if (shouldBlur) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "np_blur"
    )
    return blurRadius
}

/**
 * Full-window Glassmorphic Modal Overlay that blurs the underlying screen,
 * prevents click-through to the background, and renders a frosted glass sheet.
 */
@Composable
fun GlassModalOverlay(
    onDismissRequest: () -> Unit,
    isOverNowPlaying: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = GlassTheme.colors

    DisposableEffect(isOverNowPlaying) {
        if (isOverNowPlaying) {
            GlassModalState.activeNowPlayingModalCount += 1
        } else {
            GlassModalState.activeModalCount += 1
        }
        onDispose {
            if (isOverNowPlaying) {
                GlassModalState.activeNowPlayingModalCount =
                    (GlassModalState.activeNowPlayingModalCount - 1).coerceAtLeast(0)
            } else {
                GlassModalState.activeModalCount =
                    (GlassModalState.activeModalCount - 1).coerceAtLeast(0)
            }
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (colors.isDark) Color.Black.copy(alpha = 0.52f)
                    else Color.Black.copy(alpha = 0.28f)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismissRequest()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            // Inner container consumes clicks so clicking inside the dialog does not dismiss it
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

private var cachedLogoBitmap: ImageBitmap? = null
private var cachedDockIconBitmap: ImageBitmap? = null

fun loadGlassBeatLogoBitmap(): ImageBitmap? {
    cachedLogoBitmap?.let { return it }
    return try {
        val stream = GlassTheme::class.java.getResourceAsStream("/glassbeat_logo.png")
        if (stream != null) {
            val bytes = stream.use { it.readBytes() }
            val bmp = Image.makeFromEncoded(bytes).toComposeImageBitmap()
            cachedLogoBitmap = bmp
            bmp
        } else null
    } catch (_: Exception) {
        null
    }
}

fun loadGlassBeatDockIconBitmap(): ImageBitmap? {
    cachedDockIconBitmap?.let { return it }
    return try {
        val stream = GlassTheme::class.java.getResourceAsStream("/glassbeat_dock_icon.png")
        if (stream != null) {
            val bytes = stream.use { it.readBytes() }
            val bmp = Image.makeFromEncoded(bytes).toComposeImageBitmap()
            cachedDockIconBitmap = bmp
            bmp
        } else loadGlassBeatLogoBitmap()
    } catch (_: Exception) {
        loadGlassBeatLogoBitmap()
    }
}

@Composable
fun GlassBeatTheme(
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    val glassPalette = if (isDarkMode) DarkGlassPalette else LightGlassPalette

    val materialScheme = if (isDarkMode) {
        darkColorScheme(
            primary = glassPalette.primary,
            onPrimary = glassPalette.onPrimary,
            secondary = glassPalette.accent,
            background = glassPalette.background,
            surface = glassPalette.surface,
            surfaceVariant = glassPalette.surfaceVariant,
            onBackground = glassPalette.onSurface,
            onSurface = glassPalette.onSurface,
            onSurfaceVariant = glassPalette.onSurfaceVariant,
            error = glassPalette.danger
        )
    } else {
        lightColorScheme(
            primary = glassPalette.primary,
            onPrimary = glassPalette.onPrimary,
            secondary = glassPalette.accent,
            background = glassPalette.background,
            surface = glassPalette.surface,
            surfaceVariant = glassPalette.surfaceVariant,
            onBackground = glassPalette.onSurface,
            onSurface = glassPalette.onSurface,
            onSurfaceVariant = glassPalette.onSurfaceVariant,
            error = glassPalette.danger
        )
    }

    CompositionLocalProvider(LocalGlassColors provides glassPalette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography(),
            content = content
        )
    }
}

/**
 * Reusable Glassmorphic Card / Panel modifier with translucent frosted fill and specular border.
 */
@Composable
fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    elevated: Boolean = false,
    isSelected: Boolean = false
): Modifier {
    val colors = GlassTheme.colors
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val topFill = if (elevated) {
        if (colors.isDark) Color(0xE6222530) else Color(0xF2FFFFFF)
    } else {
        colors.glassSurface
    }
    val bottomFill = if (elevated) {
        if (colors.isDark) Color(0xCC171922) else Color(0xE0F4F6FA)
    } else {
        colors.glassSurface.copy(alpha = (colors.glassSurface.alpha * 0.90f).coerceIn(0f, 1f))
    }
    val borderColor = if (isSelected) colors.primary else colors.glassBorder
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(colors = listOf(topFill, bottomFill)),
            shape = shape
        )
        .border(BorderStroke(borderWidth, borderColor), shape)
}

@Composable
fun Modifier.glassPill(): Modifier = glassCard(cornerRadius = 50.dp, elevated = false)

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000L
    val hr = totalSec / 3600L
    val min = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (hr > 0) {
        "%d:%02d:%02d".format(hr, min, sec)
    } else {
        "%d:%02d".format(min, sec)
    }
}
