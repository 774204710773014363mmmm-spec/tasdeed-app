package com.shawafi.tasdeed.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FirebaseClient {
    const val API_KEY = "AIzaSyDRN-zYxYF3uCviIzIQQwAeRaGflTm6LUY"
    const val DB_URL = "https://nidaa-meter-app-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val ROOT = "v2_nidaa_meter"
    const val SYNC_KEY = "NM2026_V2"
    const val LOCK_DURATION = 60000L

    private const val SIGNUP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY"
    private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token?key=$API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    // ── Firebase Auth (anonymous sign-in) ──
    // يرفق التوكن بكل طلب حتى تعمل القواعد الجديدة (auth != null)
    // والتي تمنع الإصدارات القديمة من القراءة/الكتابة.

    @Volatile private var idToken: String? = null
    @Volatile private var refreshToken: String? = null
    @Volatile private var expiresAt: Long = 0

    @Synchronized
    private fun ensureToken(): Boolean {
        val now = System.currentTimeMillis()
        if (!idToken.isNullOrEmpty() && now < expiresAt - 60_000) return true
        if (!refreshToken.isNullOrEmpty()) {
            try {
                val body = JSONObject()
                    .put("grant_type", "refresh_token")
                    .put("refresh_token", refreshToken)
                client.newCall(Request.Builder().url(TOKEN_URL).post(body.toString().toRequestBody(json)).build())
                    .execute().use { resp ->
                        if (resp.isSuccessful) {
                            val o = JSONObject(resp.body?.string() ?: "{}")
                            idToken = o.optString("id_token").ifEmpty { null }
                            refreshToken = o.optString("refresh_token").ifEmpty { refreshToken }
                            expiresAt = now + (o.optLong("expires_in", 3600) * 1000)
                        }
                    }
                if (!idToken.isNullOrEmpty()) return true
            } catch (e: Exception) {}
        }
        try {
            val body = JSONObject().put("returnSecureToken", true)
            client.newCall(Request.Builder().url(SIGNUP_URL).post(body.toString().toRequestBody(json)).build())
                .execute().use { resp ->
                    if (resp.isSuccessful) {
                        val o = JSONObject(resp.body?.string() ?: "{}")
                        idToken = o.optString("idToken").ifEmpty { null }
                        refreshToken = o.optString("refreshToken").ifEmpty { null }
                        expiresAt = now + (o.optLong("expiresIn", 3600) * 1000)
                    }
                }
            return !idToken.isNullOrEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    private fun call(method: String, path: String, payload: JSONObject?): Response? {
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val token = if (ensureToken()) idToken ?: "" else ""
            // بدون توكن: الوضع المفتوح حتى تُفعّل المصادقة في الكونسول + القواعد
            val suffix = if (token.isEmpty()) "" else "?auth=$token"
            val url = "$DB_URL/$path.json$suffix"
            val body = payload?.toString()?.toRequestBody(json)
            val req = Request.Builder().url(url).method(method, body).build()
            val resp = try { client.newCall(req).execute() } catch (e: Exception) { return null }
            if (resp.code == 401 && attempt < 2) {
                resp.close()
                idToken = null
                continue
            }
            return resp
        }
        return null
    }

    fun get(path: String): JSONObject? {
        val resp = call("GET", path, null) ?: return null
        resp.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            if (body.isBlank() || body == "null") return null
            return JSONObject(body)
        }
    }

    /** HTTP 200 → النص الخام ("null"/فارغ = العقدة محذوفة/فارغة)، خطأ شبكة/سيرفر → null */
    fun getRaw(path: String): String? {
        val resp = call("GET", path, null) ?: return null
        resp.use {
            if (!it.isSuccessful) return null
            return it.body?.string()?.trim()
        }
    }

    fun post(path: String, payload: JSONObject): JSONObject? {
        val resp = call("POST", path, payload) ?: return null
        resp.use {
            if (!it.isSuccessful) return null
            return JSONObject(it.body?.string() ?: "{}")
        }
    }

    fun update(path: String, payload: JSONObject): Boolean {
        val resp = call("PATCH", path, payload) ?: return false
        resp.use { return it.isSuccessful }
    }

    fun put(path: String, payload: JSONObject): Boolean {
        val resp = call("PUT", path, payload) ?: return false
        resp.use { return it.isSuccessful }
    }

    fun delete(path: String): Boolean {
        val resp = call("DELETE", path, null) ?: return false
        resp.use { return it.isSuccessful }
    }
}