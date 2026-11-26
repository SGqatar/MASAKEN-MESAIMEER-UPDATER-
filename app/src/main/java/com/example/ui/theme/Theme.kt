package com.example.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalReducedMotion = staticCompositionLocalOf { false }

private val DarkOpsColorScheme = darkColorScheme(
    primary = OpsCyan,
    onPrimary = OpsObsidian,
    primaryContainer = OpsCyanDeep,
    onPrimaryContainer = OpsCyan,
    secondary = OpsAmber,
    onSecondary = OpsObsidian,
    secondaryContainer = OpsAmberDeep,
    onSecondaryContainer = OpsAmber,
    tertiary = OpsEmerald,
    onTertiary = OpsObsidian,
    tertiaryContainer = OpsEmeraldDeep,
    onTertiaryContainer = OpsEmerald,
    error = OpsCrimson,
    onError = OpsWhite,
    errorContainer = OpsCrimsonSurface,
    onErrorContainer = OpsCrimsonText,
    background = OpsObsidian,
    onBackground = OpsWhite,
    surface = OpsSlate,
    onSurface = OpsWhite,
    surfaceVariant = OpsConsole,
    onSurfaceVariant = OpsSteel,
    surfaceContainerLow = OpsSlate,
    surfaceContainer = OpsConsole,
    surfaceContainerHigh = OpsConsoleElevated,
    outline = OpsBorder,
    outlineVariant = OpsBorder
)

val OpsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MasakenOpsTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reducedMotion = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = DarkOpsColorScheme,
            typography = OpsTypography,
            shapes = OpsShapes,
            content = content
        )
    }
}
