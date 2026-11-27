package com.example.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Applies a responsive spring-physics scale effect on button/card presses,
 * automatically bypassing scaling when system reduced-motion is enabled.
 */
fun Modifier.springPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) pressedScale else 1f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "springPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier.springPressScale(interactionSource, pressedScale = 0.94f),
        content = content
    )
}

@Composable
fun SpringOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = border,
        colors = colors,
        interactionSource = interactionSource,
        modifier = modifier.springPressScale(interactionSource, pressedScale = 0.95f),
        content = content
    )
}

@Composable
fun SpringFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    FloatingActionButton(
        onClick = onClick,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        modifier = modifier.springPressScale(interactionSource, pressedScale = 0.91f),
        content = content
    )
}

/**
 * Smooth spring-physics list item entry + LazyColumn placement animation.
 * Honors system reduced-motion settings by snapping immediately when enabled.
 */
@Composable
fun LazyItemScope.SpringListItemContainer(
    itemKey: Any,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val alphaAnim = remember(itemKey, reducedMotion) {
        Animatable(if (reducedMotion) 1f else 0f)
    }
    val offsetYAnim = remember(itemKey, reducedMotion) {
        Animatable(if (reducedMotion) 0f else 28f)
    }
    val scaleAnim = remember(itemKey, reducedMotion) {
        Animatable(if (reducedMotion) 1f else 0.97f)
    }

    LaunchedEffect(itemKey, reducedMotion) {
        if (reducedMotion) {
            alphaAnim.snapTo(1f)
            offsetYAnim.snapTo(0f)
            scaleAnim.snapTo(1f)
        } else {
            val staggerMs = if (index < 6) index * 24L else 0L
            if (staggerMs > 0) delay(staggerMs)
            val springSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
            launch { alphaAnim.animateTo(1f, tween(180)) }
            launch { offsetYAnim.animateTo(0f, springSpec) }
            launch { scaleAnim.animateTo(1f, springSpec) }
        }
    }

    val placementModifier = if (reducedMotion) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(180),
            placementSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            fadeOutSpec = tween(150)
        )
    }

    Box(
        modifier = modifier
            .then(placementModifier)
            .graphicsLayer {
                alpha = alphaAnim.value
                translationY = offsetYAnim.value
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
    ) {
        content()
    }
}

private val screenSpringOffsetSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val screenSpringFloatSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

fun opsScreenEnterTransition(forward: Boolean, reducedMotion: Boolean): EnterTransition {
    if (reducedMotion) return EnterTransition.None
    val sign = if (forward) 1 else -1
    return slideInHorizontally(
        animationSpec = screenSpringOffsetSpec,
        initialOffsetX = { fullWidth -> sign * (fullWidth / 4) }
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = screenSpringFloatSpec
    ) + fadeIn(animationSpec = tween(180))
}

fun opsScreenExitTransition(forward: Boolean, reducedMotion: Boolean): ExitTransition {
    if (reducedMotion) return ExitTransition.None
    val sign = if (forward) -1 else 1
    return slideOutHorizontally(
        animationSpec = screenSpringOffsetSpec,
        targetOffsetX = { fullWidth -> sign * (fullWidth / 5) }
    ) + scaleOut(
        targetScale = 0.97f,
        animationSpec = screenSpringFloatSpec
    ) + fadeOut(animationSpec = tween(150))
}
