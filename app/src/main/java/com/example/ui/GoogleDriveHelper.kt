package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleDriveHelper {
    private const val TAG = "GoogleDriveHelper"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class CloudBackupItem(
        val id: String,
        val name: String,
        val createdTime: String,
        val size: Long,
        val companyName: String,
        val companyId: String,
        val dateDisplay: String,
        val timestamp: String
    )

    // Helper interface to report recover-intent
    interface AuthCallback {
        fun onAuthRequired(intent: Intent)
        fun onError(message: String)
    }

    /**
     * Grabs the OAuth 2.0 access token for Google Drive API scopes on a background thread.
     */
    suspend fun getAccessToken(
        context: Context,
        accountEmail: String,
        callback: AuthCallback? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
            try {
                // Return local/cached or fresh token
                val token = GoogleAuthUtil.getToken(context, accountEmail, scope)
                token
            } catch (userAuthEx: UserRecoverableAuthException) {
                Log.w(TAG, "User interaction required for token", userAuthEx)
                userAuthEx.intent?.let { callback?.onAuthRequired(it) }
                null
            } catch (authEx: GoogleAuthException) {
                Log.e(TAG, "Google auth exception", authEx)
                callback?.onError("حدث خطأ في مصادقة جوجل: ${authEx.message}")
                null
            } catch (ioEx: IOException) {
                Log.e(TAG, "Network or I/O error during auth", ioEx)
                callback?.onError("خطأ في الاتصال بالشبكة لحساب جوجل")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Unknown auth failure", e)
                callback?.onError("فشل غير متوقع للمصادقة: ${e.message}")
                null
            }
        }
    }

    /**
     * Clear token from Google cache if expired or failing
     */
    suspend fun invalidateToken(context: Context, token: String) {
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.invalidateToken(context, token)
            } catch (e: Exception) {
                Log.e(TAG, "Error invalidating token", e)
            }
        }
    }

    /**
     * Upload a Database file to Google Drive's appDataFolder space.
     */
    suspend fun uploadFile(
        context: Context,
        accountEmail: String,
        file: File,
        remoteName: String,
        callback: AuthCallback? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var token = getAccessToken(context, accountEmail, callback) ?: return@withContext false

            var attempts = 0
            var success = false
            while (attempts < 2 && !success) {
                attempts++
                try {
                    val metadata = JSONObject().apply {
                        put("name", remoteName)
                        put("parents", org.json.JSONArray().apply { put("appDataFolder") })
                    }

                    val metadataPart = RequestBody.create(
                        "application/json; charset=UTF-8".toMediaTypeOrNull(),
                        metadata.toString()
                    )

                    val filePart = RequestBody.create(
                        "application/octet-stream".toMediaTypeOrNull(),
                        file
                    )

                    val multipartBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addPart(metadataPart)
                        .addPart(filePart)
                        .build()

                    val request = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .header("Authorization", "Bearer $token")
                        .post(multipartBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            success = true
                            Log.i(TAG, "Successfully uploaded $remoteName to Google Drive")
                        } else if (response.code == 401) {
                            Log.w(TAG, "Access token expired/invalidated, retrying once...")
                            invalidateToken(context, token)
                            token = getAccessToken(context, accountEmail, callback) ?: return@withContext false
                        } else {
                            val errBody = response.body?.string() ?: ""
                            Log.e(TAG, "Failed upload to Google Drive: Code ${response.code} - $errBody")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception uploading file", e)
                }
            }
            success
        }
    }

    /**
     * Lists all files starting with "Backup_" from the appDataFolder space.
     */
    suspend fun listBackups(
        context: Context,
        accountEmail: String,
        callback: AuthCallback? = null
    ): List<CloudBackupItem> {
        return withContext(Dispatchers.IO) {
            var token = getAccessToken(context, accountEmail, callback) ?: return@withContext emptyList<CloudBackupItem>()

            var attempts = 0
            var items: List<CloudBackupItem>? = null
            while (attempts < 2 && items == null) {
                attempts++
                try {
                    // Query appDataFolder for all backups matching "Backup_" name prefix
                    val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name+contains+'Backup_'+and+trashed+=+false&fields=files(id,name,createdTime,size)&pageSize=100"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val resStr = response.body?.string() ?: "{}"
                            val json = JSONObject(resStr)
                            val filesArr = json.optJSONArray("files") ?: org.json.JSONArray()
                            val parsedList = mutableListOf<CloudBackupItem>()

                            for (i in 0 until filesArr.length()) {
                                val fileObj = filesArr.getJSONObject(i)
                                val id = fileObj.getString("id")
                                val name = fileObj.getString("name")
                                val createdTime = fileObj.optString("createdTime", "")
                                val size = fileObj.optLong("size", 0L)

                                val item = parseCloudFileName(id, name, createdTime, size)
                                if (item != null) {
                                    parsedList.add(item)
                                }
                            }
                            parsedList.sortByDescending { it.timestamp }
                            items = parsedList
                        } else if (response.code == 401) {
                            Log.w(TAG, "Access token expired, rewriting and retrying...")
                            invalidateToken(context, token)
                            token = getAccessToken(context, accountEmail, callback) ?: return@withContext emptyList()
                        } else {
                            val errStr = response.body?.string() ?: ""
                            Log.e(TAG, "Failed list: Code ${response.code} - $errStr")
                            items = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception listing cloud backups", e)
                }
            }
            items ?: emptyList()
        }
    }

    private fun parseCloudFileName(id: String, name: String, createdTime: String, size: Long): CloudBackupItem? {
        return try {
            val nameWithoutExt = name.substringBeforeLast(".")
            val parts = nameWithoutExt.split("_")
            if (parts.size < 4) return null

            var dateIndex = -1
            for (i in parts.indices) {
                if (parts[i].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    dateIndex = i
                    break
                }
            }
            if (dateIndex == -1 || dateIndex < 2) return null

            val companyId = parts[dateIndex - 1]
            val companyNameParsed = parts.subList(1, dateIndex - 1).joinToString(" ").replace("_", " ")
            val datePart = parts[dateIndex]
            val timePart = if (dateIndex + 1 < parts.size) parts[dateIndex + 1] else ""

            val displayTime = if (timePart.isNotBlank()) {
                val subTime = timePart.replace("-", ":")
                "$datePart | $subTime (سحابي ☁️)"
            } else {
                "$datePart (سحابي ☁️)"
            }

            val rawTimestamp = "${datePart}_${timePart}"
            val sizeMb = size.toDouble() / (1024 * 1024)
            val sizeDisplay = String.format("%.2f MB", sizeMb)

            CloudBackupItem(
                id = id,
                name = name,
                createdTime = createdTime,
                size = size,
                companyName = companyNameParsed,
                companyId = companyId,
                dateDisplay = displayTime,
                timestamp = rawTimestamp
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads a file from Google Drive into a local file.
     */
    suspend fun downloadFile(
        context: Context,
        accountEmail: String,
        fileId: String,
        destFile: File,
        callback: AuthCallback? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var token = getAccessToken(context, accountEmail, callback) ?: return@withContext false

            var attempts = 0
            var success = false
            while (attempts < 2 && !success) {
                attempts++
                try {
                    val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body ?: return@withContext false
                            body.byteStream().use { inputStream ->
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            success = true
                            Log.i(TAG, "Successfully downloaded remote file $fileId -> ${destFile.absolutePath}")
                        } else if (response.code == 401) {
                            invalidateToken(context, token)
                            token = getAccessToken(context, accountEmail, callback) ?: return@withContext false
                        } else {
                            val errStr = response.body?.string() ?: ""
                            Log.e(TAG, "Failed download: Code ${response.code} - $errStr")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception downloading file", e)
                }
            }
            success
        }
    }

    /**
     * Deletes a file from Google Drive.
     */
    suspend fun deleteFile(
        context: Context,
        accountEmail: String,
        fileId: String,
        callback: AuthCallback? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var token = getAccessToken(context, accountEmail, callback) ?: return@withContext false

            var attempts = 0
            var success = false
            while (attempts < 2 && !success) {
                attempts++
                try {
                    val url = "https://www.googleapis.com/drive/v3/files/$fileId"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .delete()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful || response.code == 404) {
                            success = true
                            Log.i(TAG, "Successfully deleted remote file $fileId")
                        } else if (response.code == 401) {
                            invalidateToken(context, token)
                            token = getAccessToken(context, accountEmail, callback) ?: return@withContext false
                        } else {
                            val errStr = response.body?.string() ?: ""
                            Log.e(TAG, "Failed delete: Code ${response.code} - $errStr")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception deleting file", e)
                }
            }
            success
        }
    }
}
