package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "InspectionAiService"

data class InspectionPhotoDraft(
    val id: String,
    val photoPath: String,
    val area: String = "General Corridor",
    val finding: String = "",
    val severity: String = "Pass", // "Pass" | "Minor" | "Urgent"
    val actionRequired: String = ""
)

class InspectionAiService(private val context: Context) {

    private val inspectionSchema = Schema.obj(
        properties = mapOf(
            "area" to Schema.string(description = "Building area or zone shown e.g. Lobby, Electrical Room, Roof, Basement, Staircase, Corridor, Facade, Pump Room"),
            "finding" to Schema.string(description = "Concise factual facility inspection observation of condition or defect"),
            "severity" to Schema.enumeration(
                values = listOf("Pass", "Minor", "Urgent"),
                description = "Severity classification"
            ),
            "actionRequired" to Schema.string(description = "Recommended corrective or preventive maintenance action, or No action needed")
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = inspectionSchema
            }
        )
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun analyzePhotosBatch(
        buildingName: String,
        drafts: List<InspectionPhotoDraft>,
        operatorHint: String = ""
    ): Result<List<InspectionPhotoDraft>> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(
                IllegalStateException("Device is offline. You can edit area, finding, and severity manually or connect to run AI organization.")
            )
        }
        try {
            val analyzed = drafts.mapIndexed { idx, draft ->
                val file = File(draft.photoPath)
                if (!file.exists()) {
                    draft
                } else {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap == null) {
                        draft
                    } else {
                        val scaled = scaleDownIfNeeded(bitmap, 1024)
                        try {
                            val prompt = buildString {
                                appendLine("Analyze photo #${idx + 1} for a building inspection report at Masaken Mesaimeer, Building: ${buildingName.ifBlank { "Property Building" }}.")
                                appendLine("Identify the specific building area/zone, concise inspection finding, severity (Pass, Minor, Urgent), and action required.")
                                if (draft.area.isNotBlank() && draft.area != "General Corridor") {
                                    appendLine("Current area label: ${draft.area}")
                                }
                                if (operatorHint.isNotBlank()) {
                                    appendLine("Inspector context: $operatorHint")
                                }
                            }
                            val response = withTimeout(60_000L) {
                                model.generateContent(
                                    content {
                                        image(scaled)
                                        text(prompt)
                                    }
                                )
                            }
                            val text = response.text ?: throw IllegalStateException("AI returned an empty inspection response.")
                            val json = JSONObject(text)
                            val severity = json.optString("severity", draft.severity).let {
                                when {
                                    it.equals("Urgent", true) -> "Urgent"
                                    it.equals("Minor", true) -> "Minor"
                                    else -> "Pass"
                                }
                            }
                            draft.copy(
                                area = json.optString("area", draft.area).ifBlank { draft.area },
                                finding = json.optString("finding", draft.finding).ifBlank { "Visual check completed" },
                                severity = severity,
                                actionRequired = json.optString("actionRequired", draft.actionRequired).ifBlank { "None" }
                            )
                        } finally {
                            if (scaled !== bitmap) bitmap.recycle()
                            scaled.recycle()
                        }
                    }
                }
            }
            Result.success(analyzed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Inspection AI timed out", e)
            Result.failure(IllegalStateException("AI inspection timed out after 60 seconds. Please retry or edit rows manually."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Inspection AI analysis failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to organize inspection photos with AI."))
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
