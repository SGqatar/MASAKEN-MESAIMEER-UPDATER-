package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.StaggeredMotionReveal
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsAmberDeep
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsCrimson
import com.example.ui.theme.OpsCrimsonBorder
import com.example.ui.theme.OpsCrimsonSurface
import com.example.ui.theme.OpsCrimsonText
import com.example.ui.theme.OpsCyan
import com.example.ui.theme.OpsCyanDeep
import com.example.ui.theme.OpsEmerald
import com.example.ui.theme.OpsEmeraldDeep
import com.example.ui.theme.OpsObsidian
import com.example.ui.theme.OpsSlate
import com.example.ui.theme.OpsSteel
import com.example.ui.theme.OpsWhite
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun launchGoogleAuth() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val response = credentialManager.getCredential(
                    context = context,
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
                Log.w("Auth", "User cancelled Google sign-in", e)
            } catch (e: GetCredentialException) {
                Log.w("Auth", "Credential Manager error", e)
                errorMessage = e.localizedMessage ?: "Google Sign-In failed. Verify Google Play services and try again."
            } catch (e: Exception) {
                Log.e("Auth", "Firebase sign-in failed", e)
                errorMessage = e.localizedMessage ?: "Unable to complete sign-in."
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OpsObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StaggeredMotionReveal(delayMillis = 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(OpsSlate)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_doha_ops_banner_1795716743874),
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
                                        OpsObsidian.copy(alpha = 0.45f),
                                        OpsObsidian.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OpsStatusPill(
                                label = "MASAKEN MESAIMEER",
                                containerColor = OpsCyanDeep.copy(alpha = 0.92f),
                                contentColor = OpsCyan
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LivePulseBeacon(color = OpsEmerald, size = 7.dp)
                                OpsStatusPill(
                                    label = "CLOUD GUARD",
                                    containerColor = OpsConsole.copy(alpha = 0.9f),
                                    contentColor = OpsEmerald
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Operator Sign-In Required",
                                style = MaterialTheme.typography.headlineSmall,
                                color = OpsWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Sign in on every session to unlock Key Register, PM Visitors, Maintenance, and Padel & Football cloud sync.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsSteel
                            )
                        }
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                StaggeredMotionReveal(delayMillis = 30) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsCrimsonSurface),
                        border = BorderStroke(1.dp, OpsCrimsonBorder),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.testTag("auth_error_banner")
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
                                contentDescription = "Sign-in error",
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
            }

            StaggeredMotionReveal(delayMillis = 70) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = OpsCyan
                            )
                            Column {
                                Text(
                                    text = "Authenticate with Google",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OpsWhite
                                )
                                Text(
                                    text = "Uses Google Credential Manager with account password / passkey verification.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsSteel
                                )
                            }
                        }

                        SpringButton(
                            onClick = { launchGoogleAuth() },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OpsCyan,
                                contentColor = OpsObsidian
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("google_signin_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = OpsObsidian,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Signing In...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Sign In with Google", fontWeight = FontWeight.Bold)
                            }
                        }

                        SpringOutlinedButton(
                            onClick = { launchGoogleAuth() },
                            enabled = !isLoading,
                            border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("google_signup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = OpsAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Sign Up / Add Google Account",
                                color = OpsAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            StaggeredMotionReveal(delayMillis = 120) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsSlate),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = OpsEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Authentication & Sync Status",
                                style = MaterialTheme.typography.titleSmall,
                                color = OpsWhite
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = OpsEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Google Sign-In & Cloud Firestore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsWhite
                                )
                            }
                            OpsStatusPill(
                                label = "ACTIVE",
                                containerColor = OpsEmeraldDeep,
                                contentColor = OpsEmerald
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = OpsAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Email/Password & Apple Sign-In require enabling those providers in the Firebase Console (Authentication → Sign-in method).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsSteel
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OpsStatusPill(
                                label = "CONSOLE",
                                containerColor = OpsAmberDeep,
                                contentColor = OpsAmber
                            )
                        }
                    }
                }
            }
        }
    }
}
