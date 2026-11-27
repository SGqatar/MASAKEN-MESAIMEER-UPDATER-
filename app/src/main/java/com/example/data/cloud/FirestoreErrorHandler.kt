package com.example.data.cloud

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

data class FirestoreErrorInfo(
    val error: String,
    val operationType: OperationType,
    val path: String?,
    val uid: String?
)

fun logFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?,
    auth: FirebaseAuth? = null
): FirestoreErrorInfo {
    val currentUser = auth?.currentUser
    val payload = JSONObject().apply {
        put("error", error.message ?: error.javaClass.simpleName)
        put("operationType", operationType.name.lowercase())
        put("path", path ?: JSONObject.NULL)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", currentUser?.uid ?: JSONObject.NULL)
                put("email", currentUser?.email ?: JSONObject.NULL)
                put("emailVerified", currentUser?.isEmailVerified ?: false)
            }
        )
    }
    Log.e("FirestoreError", payload.toString(), error)
    return FirestoreErrorInfo(
        error = error.message ?: "Unknown Firestore error",
        operationType = operationType,
        path = path,
        uid = currentUser?.uid
    )
}
