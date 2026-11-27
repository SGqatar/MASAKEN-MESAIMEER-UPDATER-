package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.db.OpsReminderEntity
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsAmberDeep
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsConsoleElevated
import com.example.ui.theme.OpsCyan
import com.example.ui.theme.OpsEmerald
import com.example.ui.theme.OpsEmeraldDeep
import com.example.ui.theme.OpsSteel
import com.example.ui.theme.OpsTabularFontFamily
import com.example.ui.theme.OpsWhite
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun LivePulseBeacon(
    color: Color,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    if (reducedMotion) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
        return
    }
    val infinite = rememberInfiniteTransition(label = "pulseBeacon")
    val haloScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloScale"
    )
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAlpha"
    )
    Box(
        modifier = modifier.size(size * 2.2f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(haloScale)
                .clip(CircleShape)
                .background(color.copy(alpha = haloAlpha))
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun StaggeredMotionReveal(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    var visible by remember { mutableStateOf(reducedMotion || delayMillis == 0) }
    LaunchedEffect(delayMillis, reducedMotion) {
        if (!visible) {
            delay(delayMillis.toLong())
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn(snap()) else {
            fadeIn(tween(220)) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 6 }
            ) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))
        },
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun AnimatedIntCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    color: Color = OpsWhite,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall
) {
    val reducedMotion = LocalReducedMotion.current
    val animated by animateIntAsState(
        targetValue = targetValue,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "intCounter"
    )
    Text(
        text = animated.toString(),
        modifier = modifier,
        color = color,
        style = style.copy(fontFamily = OpsTabularFontFamily)
    )
}

@Composable
fun AnimatedDecimalCounter(
    targetValue: Double,
    suffix: String = "",
    modifier: Modifier = Modifier,
    color: Color = OpsWhite,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge
) {
    val reducedMotion = LocalReducedMotion.current
    val animated by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "decimalCounter"
    )
    Text(
        text = String.format(Locale.US, "%.1f%s", animated, suffix),
        modifier = modifier,
        color = color,
        style = style.copy(fontFamily = OpsTabularFontFamily)
    )
}

@Composable
fun OpsMetricCard(
    title: String,
    count: Int,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulseWhenPositive: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "metricCardScale"
    )

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag(testTag)
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = OpsConsole),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (pulseWhenPositive && count > 0) {
                        LivePulseBeacon(color = accentColor, size = 7.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = title.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        color = OpsSteel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedIntCounter(
                targetValue = count,
                color = accentColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OpsSteel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OpsQuickActionPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.93f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quickActionScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            ),
        color = OpsConsoleElevated,
        contentColor = OpsWhite,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = OpsWhite,
                maxLines = 1
            )
        }
    }
}

@Composable
fun OpsStatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun OpsAlertBanner(
    reminder: OpsReminderEntity,
    accentColor: Color,
    containerColor: Color,
    onActionClick: () -> Unit,
    actionLabel: String,
    onSnoozeClick: () -> Unit,
    onDoneClick: () -> Unit,
    onPreviewNotifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = !reminder.isDone,
        enter = if (reducedMotion) EnterTransition.None else {
            fadeIn(tween(180)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        },
        exit = if (reducedMotion) ExitTransition.None else {
            fadeOut(tween(150)) + shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        },
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = OpsWhite,
                        modifier = Modifier.weight(1f)
                    )
                    if (reminder.snoozedUntilMillis != null) {
                        OpsStatusPill(
                            label = "SNOOZED 30m",
                            containerColor = OpsConsoleElevated,
                            contentColor = OpsAmber
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = reminder.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsSteel
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpringOutlinedButton(
                        onClick = onActionClick,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_open_${reminder.id}")
                    ) {
                        Text(actionLabel, color = accentColor, style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(
                        onClick = onSnoozeClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_snooze_${reminder.id}")
                    ) {
                        Icon(Icons.Filled.Snooze, contentDescription = "Snooze 30 minutes", tint = OpsSteel, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("30m", color = OpsSteel)
                    }
                    TextButton(
                        onClick = onPreviewNotifyClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_notify_${reminder.id}")
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = "Send notification", tint = OpsCyan, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onDoneClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_done_${reminder.id}")
                    ) {
                        Text("Done", color = OpsEmerald)
                    }
                }
            }
        }
    }
}

@Composable
fun DailyRoutineChecklistCard(
    reminder: OpsReminderEntity?,
    onToggleStep: (Int) -> Unit,
    onSnooze: () -> Unit,
    onMarkDone: (Boolean) -> Unit,
    onPreviewNotify: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Enter Nov 26 Padel & Football sheets (07:40 AM)",
        "Verify Nov unreturned keys (M-A04-16, M-F30-18) & 4 Move-In T-codes (SI 1638–1645)",
        "Confirm daily PM Visitors & Apartment Viewings",
        "Review open Maintenance Work Orders"
    )
    val reducedMotion = LocalReducedMotion.current
    val mask = reminder?.checklistMask ?: 0
    val completedCount = (0 until steps.size).count { (mask and (1 shl it)) != 0 }
    val isDone = reminder?.isDone == true
    val progressColor by animateColorAsState(
        targetValue = if (isDone) OpsEmerald else OpsAmber,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "checklistColor"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = completedCount / steps.size.toFloat(),
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "checklistProgress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) OpsEmeraldDeep.copy(alpha = 0.55f) else OpsConsole
        ),
        border = BorderStroke(1.dp, if (isDone) OpsEmerald.copy(alpha = 0.5f) else OpsAmber.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OpsStatusPill(
                        label = "DAILY 07:40 AM ROUTINE",
                        containerColor = OpsAmberDeep,
                        contentColor = OpsAmber
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Morning Sports & Operations Check",
                        style = MaterialTheme.typography.titleMedium,
                        color = OpsWhite
                    )
                }
                AnimatedContent(
                    targetState = completedCount,
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(tween(180)) + scaleIn(
                                initialScale = 0.85f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "checklistCountAnim"
                ) { count ->
                    Text(
                        text = "$count/${steps.size}",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = OpsTabularFontFamily),
                        color = progressColor
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = OpsBorder
            )
            Spacer(Modifier.height(12.dp))

            steps.forEachIndexed { idx, label ->
                val checked = (mask and (1 shl idx)) != 0
                val stepInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPressScale(stepInteraction, pressedScale = 0.98f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = stepInteraction,
                            indication = androidx.compose.material3.ripple(),
                            enabled = reminder != null
                        ) { onToggleStep(idx) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (checked) "Completed" else "Pending",
                        tint = if (checked) OpsEmerald else OpsSteel,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (checked) OpsSteel else OpsWhite,
                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }

            if (reminder != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onSnooze,
                        modifier = Modifier.testTag("daily_0740_snooze")
                    ) {
                        Icon(Icons.Filled.Snooze, contentDescription = null, tint = OpsSteel, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Snooze 30m", color = OpsSteel)
                    }
                    TextButton(
                        onClick = onPreviewNotify,
                        modifier = Modifier.testTag("daily_0740_notify")
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test Alert", color = OpsCyan)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onMarkDone(!isDone) },
                        modifier = Modifier.testTag("daily_0740_done")
                    ) {
                        Text(if (isDone) "Re-open" else "Complete", color = OpsEmerald)
                    }
                }
            }
        }
    }
}
