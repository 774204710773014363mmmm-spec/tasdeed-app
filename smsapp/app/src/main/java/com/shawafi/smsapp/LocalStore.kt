package com.shawafi.smsapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val sp = context.getSharedPreferences("sms_store", Context.MODE_PRIVATE)

    fun getString(key: String): String? = sp.getString(key, null)

    fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    private fun getJsonArray(key: String): JSONArray? {
        val s = getString(key) ?: return null
        return try { JSONArray(s) } catch (e: Exception) { null }
    }

    private fun getJsonObject(key: String): JSONObject? {
        val s = getString(key) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    private fun putJson(key: String, obj: Any) {
        putString(key, obj.toString())
    }

    fun saveSmsRows(list: List<SmsRow>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        putJson("sms_rows", arr)
    }

    fun loadSmsRows(): MutableList<SmsRow> {
        val out = mutableListOf<SmsRow>()
        getJsonArray("sms_rows")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(SmsRow.from(it)) }
            }
        }
        return out
    }

    fun saveSmsHistory(list: List<SmsHistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        putJson("sms_history", arr)
    }

    fun loadSmsHistory(): MutableList<SmsHistoryEntry> {
        val out = mutableListOf<SmsHistoryEntry>()
        getJsonArray("sms_history")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(SmsHistoryEntry.from(it)) }
            }
        }
        return out
    }

    fun saveSmsSettings(s: SmsSettings) {
        putJson("sms_settings", s.toJson())
    }

    fun loadSmsSettings(): SmsSettings = SmsSettings.from(getJsonObject("sms_settings"))
}