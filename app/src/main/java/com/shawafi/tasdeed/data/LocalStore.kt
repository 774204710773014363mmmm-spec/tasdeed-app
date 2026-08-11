package com.shawafi.tasdeed.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val sp = context.getSharedPreferences("tasdeed_store", Context.MODE_PRIVATE)

    fun getString(key: String): String? = sp.getString(key, null)

    fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        sp.edit().remove(key).apply()
    }

    fun getBool(key: String, def: Boolean = false): Boolean = sp.getBoolean(key, def)

    fun putBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    fun getJsonArray(key: String): JSONArray? {
        val s = getString(key) ?: return null
        return try { JSONArray(s) } catch (e: Exception) { null }
    }

    fun getJsonObject(key: String): JSONObject? {
        val s = getString(key) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    fun putJson(key: String, obj: Any) {
        putString(key, obj.toString())
    }

    fun savePayments(list: List<PaymentRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        putJson("archive_payments", arr)
    }

    fun loadPayments(): MutableList<PaymentRecord> {
        val out = mutableListOf<PaymentRecord>()
        getJsonArray("archive_payments")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(PaymentRecord.from(it)) }
            }
        }
        return out
    }

    fun savePeriods(periods: List<Period>) {
        val arr = JSONArray()
        periods.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            o.put("created_at", p.createdAt)
            o.put("closed_at", p.closedAt)
            val pays = JSONArray()
            p.payments.forEach { pays.put(it.toJson()) }
            o.put("payments", pays)
            arr.put(o)
        }
        putJson("archive_periods", arr)
    }

    fun loadPeriods(): MutableList<Period> {
        val out = mutableListOf<Period>()
        getJsonArray("archive_periods")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = Period(
                    name = o.optString("name"),
                    createdAt = o.optString("created_at"),
                    closedAt = o.optLong("closed_at", System.currentTimeMillis())
                )
                o.optJSONArray("payments")?.let { pays ->
                    for (j in 0 until pays.length()) {
                        pays.optJSONObject(j)?.let { p.payments.add(PaymentRecord.from(it)) }
                    }
                }
                out.add(p)
            }
        }
        return out
    }
}
