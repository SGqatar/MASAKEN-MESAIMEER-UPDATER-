package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "BookingParserService"

data class ParsedBookingDraft(
    val sport: String = "PADEL",
    val date: String = "26/11/2026",
    val time: String = "18:00 - 19:00",
    val name: String = "",
    val unitDetails: String = "",
    val discount: String = "FREE",
    val referenceNo: String = "",
    val durationPlayed: Double = 1.0,
    val remarks: String = "",
    val attachmentPath: String? = null
)

class BookingParserService(private val context: Context) {

    private val bookingSchema = Schema.obj(
        properties = mapOf(
            "sport" to Schema.enumeration(
                values = listOf("FOOTBALL", "PADEL"),
                description = "Sport type: FOOTBALL or PADEL"
            ),
            "date" to Schema.string(description = "Booking date in dd/MM/yyyy format, e.g. 26/11/2026"),
            "time" to Schema.string(description = "Time slot, e.g. 18:00 - 19:30"),
            "name" to Schema.string(description = "Resident or player full name"),
            "unitDetails" to Schema.string(description = "Masaken Mesaimeer unit code such as M-A04-16 or flat details"),
            "discount" to Schema.string(description = "Discount label, usually FREE"),
            "referenceNo" to Schema.string(description = "Booking reference or T-code"),
            "durationPlayed" to Schema.double(description = "Duration played in hours, e.g. 1.0 or 1.5"),
            "remarks" to Schema.string(description = "Any operational remarks from the booking")
        ),
        optionalProperties = listOf("discount", "referenceNo", "remarks")
    )

    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = bookingSchema
            }
        )
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun savePickedPhotoLocally(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val outFile = File(dir, "booking_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val rawBitmap = BitmapFactory.decodeStream(input) ?: return@withContext null
                val scaled = scaleDownIfNeeded(rawBitmap, 1600)
                FileOutputStream(outFile).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                }
                if (scaled !== rawBitmap) rawBitmap.recycle()
                scaled.recycle()
            } ?: return@withContext null
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving picked booking photo", e)
            null
        }
    }

    fun parseTextOffline(rawText: String, attachmentPath: String? = null): ParsedBookingDraft {
        val text = rawText.trim()
        val lower = text.lowercase(Locale.ROOT)

        val sport = when {
            lower.contains("football") || lower.contains("soccer") || lower.contains("pitch") || lower.contains("fb") -> "FOOTBALL"
            else -> "PADEL"
        }

        val unitRegex = Regex("""\b[Mm]-?[A-Za-z]\d{2}-\d{2}\b|\b[A-Za-z]\d{2}-\d{2}\b""")
        val unitMatch = unitRegex.find(text)?.value?.uppercase(Locale.ROOT)?.let {
            if (it.startsWith("M-")) it else if (it.startsWith("M")) "M-${it.drop(1)}" else "M-$it"
        } ?: ""

        val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
        val dateMatch = dateRegex.find(text)?.value?.replace('-', '/') ?: "26/11/2026"

        val timeRangeRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:am|pm)?\s*[-–to]+\s*\d{1,2}:\d{2}\s*(?:am|pm)?)\b""", RegexOption.IGNORE_CASE)
        val singleTimeRegex = Regex("""\b(\d{1,2}:\d{2})\b""")
        val timeRange = timeRangeRegex.find(text)?.value ?: singleTimeRegex.find(text)?.value?.let { "$it - ${addOneHour(it)}" } ?: "18:00 - 19:00"

        val refRegex = Regex("""\b(T-[A-Z0-9-]+|REF[-:\s]*[A-Z0-9-]+)\b""", RegexOption.IGNORE_CASE)
        val refNo = refRegex.find(text)?.value?.uppercase(Locale.ROOT)?.replace("REF:", "")?.trim() ?: ""

        val durationFromHours = Regex("""(\d+(?:\.\d+)?)\s*(?:hrs?|hours?)\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val durationFromMins = Regex("""(\d+)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.div(60.0)
        val duration = ((durationFromHours ?: durationFromMins ?: estimateDurationFromSlot(timeRange)) * 2.0).roundToInt() / 2.0

        val discount = if (lower.contains("paid") && !lower.contains("free")) "PAID" else "FREE"

        val nameLine = Regex("""(?:name|player|booked by|resident)\s*[:\-]\s*([^\n,]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: text.lines().firstOrNull { line ->
                val l = line.trim()
                l.length in 3..40 &&
                    !l.contains("padel", true) &&
                    !l.contains("football", true) &&
                    !dateRegex.containsMatchIn(l) &&
                    !timeRangeRegex.containsMatchIn(l)
            }?.trim() ?: ""

        val remarks = if (text.length > 80) text.take(80).replace("\n", " ") else text.replace("\n", " ")

        return ParsedBookingDraft(
            sport = sport,
            date = normalizeSlashDate(dateMatch),
            time = timeRange,
            name = nameLine,
            unitDetails = unitMatch,
            discount = discount,
            referenceNo = refNo,
            durationPlayed = duration.coerceIn(0.5, 4.0),
            remarks = if (remarks.isNotBlank()) "Parsed note: $remarks" else "Quick-entry log",
            attachmentPath = attachmentPath
        )
    }

    suspend fun extractFromPhotoWithGemini(
        imagePath: String,
        optionalHint: String
    ): Result<ParsedBookingDraft> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(
                IllegalStateException("Device is offline. Connect to the internet for Gemini Vision, or use the offline text parser below.")
            )
        }

        val file = File(imagePath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalStateException("Attached photo file could not be read."))
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return@withContext Result.failure(IllegalStateException("Could not decode attached image."))

        val scaledBitmap = scaleDownIfNeeded(bitmap, 1280)
        try {
            val prompt = buildString {
                appendLine("Extract Masaken Mesaimeer court booking details from this booking screenshot or log photo.")
                appendLine("Rules:")
                appendLine("- sport must be FOOTBALL or PADEL.")
                appendLine("- date must use dd/MM/yyyy (default to 26/11/2026 if month/year omitted).")
                appendLine("- unitDetails should match Masaken unit format like M-A04-16 if visible.")
                appendLine("- discount defaults to FREE if resident complimentary booking or not billed.")
                appendLine("- durationPlayed is in decimal hours (e.g. 1.0 or 1.5).")
                if (optionalHint.isNotBlank()) {
                    appendLine("Additional desk note: $optionalHint")
                }
            }

            val response = withTimeout(60_000L) {
                visionModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("Gemini returned an empty response.")
            val json = JSONObject(jsonText)
            val draft = ParsedBookingDraft(
                sport = json.optString("sport", "PADEL").uppercase(Locale.ROOT).let {
                    if (it == "FOOTBALL") "FOOTBALL" else "PADEL"
                },
                date = json.optString("date", "26/11/2026").ifBlank { "26/11/2026" },
                time = json.optString("time", "18:00 - 19:00").ifBlank { "18:00 - 19:00" },
                name = json.optString("name", ""),
                unitDetails = json.optString("unitDetails", ""),
                discount = json.optString("discount", "FREE").ifBlank { "FREE" },
                referenceNo = json.optString("referenceNo", ""),
                durationPlayed = json.optDouble("durationPlayed", 1.0).let {
                    if (it.isNaN() || it <= 0.0) 1.0 else it
                },
                remarks = json.optString("remarks", "Extracted via Gemini Vision"),
                attachmentPath = imagePath
            )
            Result.success(draft)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Vision extraction timed out", e)
            Result.failure(IllegalStateException("Gemini Vision timed out after 60 seconds. Please retry or edit fields manually."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Vision extraction failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Gemini Vision extraction failed."))
        } finally {
            if (scaledBitmap !== bitmap) bitmap.recycle()
            scaledBitmap.recycle()
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun normalizeSlashDate(raw: String): String {
        val parts = raw.split("/")
        if (parts.size != 3) return raw
        val d = parts[0].padStart(2, '0')
        val m = parts[1].padStart(2, '0')
        val y = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
        return "$d/$m/$y"
    }

    private fun addOneHour(hhmm: String): String {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return "19:00"
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return String.format("%02d:%02d", (h + 1) % 24, m)
    }

    private fun estimateDurationFromSlot(slot: String): Double {
        val matches = Regex("""(\d{1,2}):(\d{2})""").findAll(slot).toList()
        if (matches.size < 2) return 1.0
        val startMin = matches[0].groupValues[1].toInt() * 60 + matches[0].groupValues[2].toInt()
        val endMin = matches[1].groupValues[1].toInt() * 60 + matches[1].groupValues[2].toInt()
        val diff = if (endMin > startMin) endMin - startMin else 60
        return (diff / 60.0).coerceIn(0.5, 4.0)
    }
}
