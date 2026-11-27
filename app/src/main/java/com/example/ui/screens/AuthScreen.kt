package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.StaggeredMotionReveal
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsAmberDeep
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsConsoleElevated
import com.example.ui.theme.OpsCrimson
import com.example.ui.theme.OpsCrimsonBorder
import com.example.ui.theme.OpsCrimsonSurface
import com.example.ui.theme.OpsCrimsonText
import com.example.ui.theme.OpsCyan
import com.example.ui.theme.OpsCyanDeep
import com.example.ui.theme.OpsEmerald
import com.example.ui.theme.OpsObsidian
import com.example.ui.theme.OpsSlate
import com.example.ui.theme.OpsSteel
import com.example.ui.theme.OpsWhite
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class AuthTabMode(val label: String) {
    SIGN_IN("Sign In"),
    SIGN_UP("Sign Up")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var authMode by remember { mutableStateOf(AuthTabMode.SIGN_IN) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Subtle ambient drift + medallion float when reduced-motion is off
    val ambientDrift: Float
    val medallionFloat: Float
    if (reducedMotion) {
        ambientDrift = 0.5f
        medallionFloat = 0f
    } else {
        val infinite = rememberInfiniteTransition(label = "authAmbient")
        ambientDrift = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(6500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambientDrift"
        ).value
        medallionFloat = infinite.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "medallionFloat"
        ).value
    }

    val activeAccent by animateColorAsState(
        targetValue = if (authMode == AuthTabMode.SIGN_IN) OpsCyan else OpsAmber,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "activeAccent"
    )

    fun launchGoogleCredentialFlow(useSavedAccountFilter: Boolean) {
        if (isLoading) return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val webClientId = try {
                    context.getString(R.string.default_web_client_id)
                } catch (_: Exception) {
                    errorMessage = "Google Sign-In configuration missing: default_web_client_id not found."
                    return@launch
                }

                // Single credential option per GetCredentialRequest for API 36 responsiveness
                val credentialOption = if (useSavedAccountFilter) {
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(true)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()
                } else {
                    GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
                }

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(credentialOption)
                    .build()

                val activityContext = context.findActivity() ?: context
                val response = credentialManager.getCredential(
                    context = activityContext,
                    request = request
                )
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                    Firebase.auth.signInWithCredential(firebaseCred).await()
                    onAuthenticated()
                } else {
                    errorMessage = "Unsupported credential returned. Please choose a Google account."
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            } catch (e: NoCredentialException) {
                Log.w("Auth", "No matching saved Google credential found", e)
                errorMessage = if (useSavedAccountFilter) {
                    "No saved Google account found on this device. Tap Continue with Google instead."
                } else {
                    "No Google account is signed in on this device. Add a Google account in Settings and retry."
                }
            } catch (e: GetCredentialException) {
                Log.w("Auth", "Credential Manager error: ${e.message}", e)
                errorMessage = e.localizedMessage
                    ?: "Google Sign-In failed. Verify Google Play services and try again."
            } catch (e: FirebaseAuthException) {
                Log.e("Auth", "Firebase authentication rejected credential: ${e.errorCode}", e)
                errorMessage = e.localizedMessage ?: "Firebase authentication failed (${e.errorCode})."
            } catch (e: Exception) {
                Log.e("Auth", "Unexpected sign-in error", e)
                errorMessage = e.localizedMessage ?: "Unable to complete authentication."
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OpsObsidian)
            .drawBehind {
                // Atmospheric radial glows behind the landing layout
                val cyanCenter = Offset(
                    x = size.width * (0.22f + 0.14f * ambientDrift),
                    y = size.height * 0.16f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OpsCyan.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = cyanCenter,
                        radius = size.maxDimension * 0.55f
                    ),
                    center = cyanCenter,
                    radius = size.maxDimension * 0.55f
                )
                val warmCenter = Offset(
                    x = size.width * (0.82f - 0.12f * ambientDrift),
                    y = size.height * 0.78f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OpsAmber.copy(alpha = 0.11f),
                            Color.Transparent
                        ),
                        center = warmCenter,
                        radius = size.maxDimension * 0.5f
                    ),
                    center = warmCenter,
                    radius = size.maxDimension * 0.5f
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Architectural Hero Banner + Overlapping Floating Key Medallion
            StaggeredMotionReveal(delayMillis = 0) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp)
                            .height(216.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(OpsSlate)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.img_doha_ops_banner_1795716743874)
                                .crossfade(false)
                                .build(),
                            contentDescription = "Masaken Mesaimeer Doha residence",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            OpsObsidian.copy(alpha = 0.30f),
                                            OpsObsidian.copy(alpha = 0.72f),
                                            OpsObsidian.copy(alpha = 0.96f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OpsStatusPill(
                                    label = "MASAKEN MESAIMEER",
                                    containerColor = OpsCyanDeep.copy(alpha = 0.90f),
                                    contentColor = OpsCyan
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LivePulseBeacon(color = OpsEmerald, size = 7.dp)
                                    OpsStatusPill(
                                        label = "DOHA · LIVE",
                                        containerColor = OpsConsole.copy(alpha = 0.88f),
                                        contentColor = OpsEmerald
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Masaken Mesaimeer",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = OpsWhite,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Keys · PM Visitors · Maintenance · Sports Courts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsSteel,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Overlapping Key Emblem Medallion
                    Surface(
                        shape = CircleShape,
                        color = OpsConsoleElevated,
                        border = BorderStroke(1.5.dp, activeAccent.copy(alpha = 0.75f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .size(58.dp)
                            .graphicsLayer { translationY = medallionFloat.dp.toPx() }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            activeAccent.copy(alpha = 0.22f),
                                            OpsConsoleElevated
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = "Masaken Mesaimeer Access Key",
                                tint = activeAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Animated Inline Error Banner
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = if (reducedMotion) fadeIn(snap()) else fadeIn(tween(180)) + expandVertically(),
                exit = if (reducedMotion) fadeOut(snap()) else fadeOut(tween(150)) + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsCrimsonSurface),
                    border = BorderStroke(1.dp, OpsCrimsonBorder),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("auth_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Authentication error",
                            tint = OpsCrimson
                        )
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsCrimsonText
                        )
                    }
                }
            }

            // Primary Auth Card with Sliding Pill Mode Switcher
            StaggeredMotionReveal(delayMillis = 70) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole.copy(alpha = 0.94f)),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Spring-Animated Sliding Pill Tab Switcher
                        SlidingAuthModeSwitcher(
                            selectedMode = authMode,
                            accentColor = activeAccent,
                            enabled = !isLoading,
                            reducedMotion = reducedMotion,
                            onSelect = { mode ->
                                authMode = mode
                                errorMessage = null
                            }
                        )

                        // Smooth Slide + Fade Content Transition
                        AnimatedContent(
                            targetState = authMode,
                            transitionSpec = {
                                if (reducedMotion) {
                                    fadeIn(snap()) togetherWith fadeOut(snap())
                                } else {
                                    val toSignUp = targetState == AuthTabMode.SIGN_UP
                                    (fadeIn(tween(220)) + slideInHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        initialOffsetX = { full -> if (toSignUp) full / 6 else -full / 6 }
                                    )) togetherWith (fadeOut(tween(150)) + slideOutHorizontally(
                                        targetOffsetX = { full -> if (toSignUp) -full / 8 else full / 8 }
                                    ))
                                }
                            },
                            label = "authModeContent"
                        ) { mode ->
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (mode == AuthTabMode.SIGN_IN) OpsCyanDeep else OpsAmberDeep,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (mode == AuthTabMode.SIGN_IN) Icons.Filled.Lock else Icons.Filled.PersonAdd,
                                                contentDescription = null,
                                                tint = if (mode == AuthTabMode.SIGN_IN) OpsCyan else OpsAmber,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = if (mode == AuthTabMode.SIGN_IN) {
                                                "Welcome Back, Operator"
                                            } else {
                                                "Create Operator Workspace"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = OpsWhite
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (mode == AuthTabMode.SIGN_IN) {
                                                "Verify with Google Identity to unlock live ledgers."
                                            } else {
                                                "Register with Google to sync your ledgers to the cloud."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OpsSteel
                                        )
                                    }
                                }

                                SpringButton(
                                    onClick = { launchGoogleCredentialFlow(useSavedAccountFilter = false) },
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (mode == AuthTabMode.SIGN_IN) OpsCyan else OpsAmber,
                                        contentColor = OpsObsidian
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag(
                                            if (mode == AuthTabMode.SIGN_IN) "google_signin_button" else "google_signup_button"
                                        )
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = OpsObsidian,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text("Verifying Identity...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = if (mode == AuthTabMode.SIGN_IN) Icons.Filled.AccountCircle else Icons.Filled.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (mode == AuthTabMode.SIGN_IN) {
                                                "Continue with Google"
                                            } else {
                                                "Sign Up with Google"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (mode == AuthTabMode.SIGN_IN) {
                                    SpringOutlinedButton(
                                        onClick = { launchGoogleCredentialFlow(useSavedAccountFilter = true) },
                                        enabled = !isLoading,
                                        border = BorderStroke(1.dp, OpsBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .minimumInteractiveComponentSize()
                                            .testTag("google_saved_account_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Key,
                                            contentDescription = null,
                                            tint = OpsCyan,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Quick Sign-In (Saved Account)",
                                            color = OpsWhite,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Clean Minimal Trust Strip
            StaggeredMotionReveal(delayMillis = 130) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrustBadgeItem(
                        icon = Icons.Filled.VerifiedUser,
                        label = "Credential Manager",
                        tint = OpsCyan
                    )
                    TrustBadgeItem(
                        icon = Icons.Filled.CloudDone,
                        label = "Encrypted Cloud Sync",
                        tint = OpsEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun SlidingAuthModeSwitcher(
    selectedMode: AuthTabMode,
    accentColor: Color,
    enabled: Boolean,
    reducedMotion: Boolean,
    onSelect: (AuthTabMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = OpsSlate,
        border = BorderStroke(1.dp, OpsBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / AuthTabMode.entries.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedMode.ordinal,
                animationSpec = if (reducedMotion) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                },
                label = "tabIndicatorOffset"
            )

            // Sliding Highlight Pill
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f))
            )

            Row(modifier = Modifier.fillMaxSize()) {
                AuthTabMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) accentColor else OpsSteel,
                        animationSpec = if (reducedMotion) snap() else tween(180),
                        label = "tabTextColor"
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = androidx.compose.material3.ripple(),
                                enabled = enabled
                            ) { onSelect(mode) }
                            .testTag("auth_tab_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustBadgeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OpsSteel
        )
    }
}
