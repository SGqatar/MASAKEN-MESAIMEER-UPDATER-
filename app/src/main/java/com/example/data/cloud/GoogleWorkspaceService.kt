package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed interface WorkspaceAuthOutcome {
    data class Authorized(val accessToken: String) : WorkspaceAuthOutcome
    data class NeedsResolution(val intentSenderRequest: IntentSenderRequest) : WorkspaceAuthOutcome
    data class Error(val message: String) : WorkspaceAuthOutcome
}

data class DriveUploadOutcome(
    val fileId: String,
    val fileName: String,
    val webViewLink: String
)

data class EmailAttachmentBytes(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

class GoogleWorkspaceService {

    companion object {
        const val SCOPE_GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_ROOT_FOLDER = "Masaken Mesaimeer Ops"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedToken: String? = null

    fun getCachedToken(): String? = cachedToken

    suspend fun authorizeScopes(activity: Activity): WorkspaceAuthOutcome {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE_GMAIL_SEND), Scope(SCOPE_DRIVE_FILE)))
                .build()
            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    WorkspaceAuthOutcome.NeedsResolution(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    WorkspaceAuthOutcome.Error("Google Workspace consent resolution was unavailable.")
                }
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    WorkspaceAuthOutcome.Error("No OAuth access token returned by Google Workspace.")
                } else {
                    cachedToken = token
                    WorkspaceAuthOutcome.Authorized(token)
                }
            }
        } catch (e: Exception) {
            WorkspaceAuthOutcome.Error(e.localizedMessage ?: "Google Workspace authorization failed.")
        }
    }

    fun handleAuthorizationResult(activity: Activity, data: Intent?): WorkspaceAuthOutcome {
        return try {
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                WorkspaceAuthOutcome.Error("Authorization declined or returned an empty token.")
            } else {
                cachedToken = token
                WorkspaceAuthOutcome.Authorized(token)
            }
        } catch (e: Exception) {
            WorkspaceAuthOutcome.Error(e.localizedMessage ?: "Could not complete Google Workspace sign-in.")
        }
    }

    suspend fun uploadToDriveFolder(
        accessToken: String,
        subfolderName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<DriveUploadOutcome> = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = ensureDriveFolder(accessToken, DRIVE_ROOT_FOLDER, parentId = null)
            val targetFolderId = if (subfolderName.isBlank()) {
                rootFolderId
            } else {
                ensureDriveFolder(accessToken, subfolderName, parentId = rootFolderId)
            }

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(targetFolderId))
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .addPart(
                    bytes.toRequestBody(mimeType.toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) cachedToken = null
                    return@withContext Result.failure(
                        IllegalStateException("Drive upload failed (${response.code}): ${raw.take(220)}")
                    )
                }
                val json = JSONObject(raw)
                Result.success(
                    DriveUploadOutcome(
                        fileId = json.optString("id"),
                        fileName = json.optString("name", fileName),
                        webViewLink = json.optString("webViewLink")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ensureDriveFolder(accessToken: String, name: String, parentId: String?): String {
        val escapedName = name.replace("'", "\\'")
        val parentClause = if (parentId != null) " and '$parentId' in parents" else " and 'root' in parents"
        val query = "name='$escapedName' and mimeType='application/vnd.google-apps.folder' and trashed=false$parentClause"
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)&pageSize=1")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(searchReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val files = JSONObject(raw).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            } else if (resp.code == 401) {
                cachedToken = null
                throw IllegalStateException("Google Workspace session expired. Re-authorize and retry.")
            }
        }

        val createJson = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) {
                put("parents", JSONArray().put(parentId))
            }
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(createJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(createReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed creating Drive folder '$name' (${resp.code}): ${raw.take(200)}")
            }
            return JSONObject(raw).getString("id")
        }
    }

    suspend fun sendEmailViaGmailApi(
        accessToken: String,
        toAddresses: List<String>,
        ccAddresses: List<String>,
        subject: String,
        plainBody: String,
        htmlBody: String,
        attachments: List<EmailAttachmentBytes>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanTo = toAddresses.map { it.trim() }.filter { it.isNotBlank() }
            if (cleanTo.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Enter at least one 'To' recipient email address."))
            }
            val cleanCc = ccAddresses.map { it.trim() }.filter { it.isNotBlank() }

            val mixedBoundary = "=_MasakenMixed_${System.currentTimeMillis()}"
            val altBoundary = "=_MasakenAlt_${System.currentTimeMillis()}"

            val mime = buildString {
                append("To: ").append(cleanTo.joinToString(", ")).append("\r\n")
                if (cleanCc.isNotEmpty()) {
                    append("Cc: ").append(cleanCc.joinToString(", ")).append("\r\n")
                }
                append("Subject: =?UTF-8?B?")
                    .append(Base64.encodeToString(subject.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                    .append("?=\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: multipart/mixed; boundary=\"$mixedBoundary\"\r\n\r\n")

                append("--$mixedBoundary\r\n")
                append("Content-Type: multipart/alternative; boundary=\"$altBoundary\"\r\n\r\n")

                append("--$altBoundary\r\n")
                append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
                append("Content-Transfer-Encoding: base64\r\n\r\n")
                append(chunkedBase64(plainBody.toByteArray(Charsets.UTF_8))).append("\r\n")

                append("--$altBoundary\r\n")
                append("Content-Type: text/html; charset=\"UTF-8\"\r\n")
                append("Content-Transfer-Encoding: base64\r\n\r\n")
                append(chunkedBase64(htmlBody.toByteArray(Charsets.UTF_8))).append("\r\n")

                append("--$altBoundary--\r\n")

                attachments.forEach { att ->
                    val safeName = att.fileName.replace("\"", "_")
                    append("--$mixedBoundary\r\n")
                    append("Content-Type: ${att.mimeType}; name=\"$safeName\"\r\n")
                    append("Content-Disposition: attachment; filename=\"$safeName\"\r\n")
                    append("Content-Transfer-Encoding: base64\r\n\r\n")
                    append(chunkedBase64(att.bytes)).append("\r\n")
                }

                append("--$mixedBoundary--\r\n")
            }

            val encodedRaw = Base64.encodeToString(
                mime.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val payload = JSONObject().put("raw", encodedRaw).toString()

            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawResp = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) cachedToken = null
                    return@withContext Result.failure(
                        IllegalStateException("Gmail send failed (${response.code}): ${rawResp.take(220)}")
                    )
                }
                val id = JSONObject(rawResp).optString("id", "sent")
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchShareEmailChooser(
        context: Context,
        toAddresses: List<String>,
        ccAddresses: List<String>,
        subject: String,
        plainBody: String,
        attachmentUris: List<Uri>
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_EMAIL, toAddresses.filter { it.isNotBlank() }.toTypedArray())
                if (ccAddresses.any { it.isNotBlank() }) {
                    putExtra(Intent.EXTRA_CC, ccAddresses.filter { it.isNotBlank() }.toTypedArray())
                }
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, plainBody)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Send Report via Mail App").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun chunkedBase64(bytes: ByteArray): String {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val sb = StringBuilder(b64.length + b64.length / 76 * 2)
        var idx = 0
        while (idx < b64.length) {
            val end = (idx + 76).coerceAtMost(b64.length)
            sb.append(b64, idx, end)
            if (end < b64.length) sb.append("\r\n")
            idx = end
        }
        return sb.toString()
    }
}
