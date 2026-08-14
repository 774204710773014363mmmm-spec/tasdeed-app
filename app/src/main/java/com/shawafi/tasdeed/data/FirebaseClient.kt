package com.shawafi.tasdeed.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FirebaseClient {
    const val API_KEY = "AIzaSyDRN-zYxYF3uCviIzIQQwAeRaGflTm6LUY"
    const val DB_URL = "https://nidaa-meter-app-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val ROOT = "v2_nidaa_meter"
    const val SYNC_KEY = "NM2026_V2"
    const val LOCK_DURATION = 60000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    fun get(path: String): JSONObject? {
        val url = "$DB_URL/$path.json"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            if (body.isBlank() || body == "null") return null
            return JSONObject(body)
        }
    }

    /** HTTP 200 → النص الخام ("null"/فارغ = العقدة محذوفة/فارغة)، خطأ شبكة/سيرفر → null */
    fun getRaw(path: String): String? {
        val url = "$DB_URL/$path.json"
        val req = Request.Builder().url(url).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()?.trim()
            }
        } catch (e: Exception) { null }
    }

    fun post(path: String, payload: JSONObject): JSONObject? {
        val url = "$DB_URL/$path.json"
        val req = Request.Builder().url(url)
            .post(payload.toString().toRequestBody(json)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    fun update(path: String, payload: JSONObject): Boolean {
        val url = "$DB_URL/$path.json"
        val req = Request.Builder().url(url)
            .patch(payload.toString().toRequestBody(json)).build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }

    fun put(path: String, payload: JSONObject): Boolean {
        val url = "$DB_URL/$path.json"
        val req = Request.Builder().url(url)
            .put(payload.toString().toRequestBody(json)).build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }

    fun delete(path: String): Boolean {
        val url = "$DB_URL/$path.json"
        val req = Request.Builder().url(url).delete().build()
        client.newCall(req).execute().use { resp -> return resp.isSuccessful }
    }
}
