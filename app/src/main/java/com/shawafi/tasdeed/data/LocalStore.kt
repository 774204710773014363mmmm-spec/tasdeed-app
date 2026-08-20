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

    fun getInt(key: String, def: Int = 0): Int = sp.getInt(key, def)

    fun putInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
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

    private fun keyFor(base: String, branch: String?): String =
        if (branch.isNullOrBlank()) base else "${base}_${branch}"

    // ترحيل البيانات القديمة (المفتاح العام) إلى فرع واحد عند أول دخول به بعد الترقية
    private fun migrateLegacy(base: String, branch: String?): JSONArray? {
        if (branch.isNullOrBlank()) return getJsonArray(base)
        val legacy = getJsonArray(base) ?: return null
        val branchKey = keyFor(base, branch)
        if (getJsonArray(branchKey) != null) return null
        var allMatch = true
        for (i in 0 until legacy.length()) {
            val o = legacy.optJSONObject(i) ?: continue
            val pays = o.optJSONArray("payments") ?: continue
            for (j in 0 until pays.length()) {
                val po = pays.optJSONObject(j) ?: continue
                val b = po.optString("branch")
                if (b.isNotBlank() && b != branch) { allMatch = false; break }
            }
            if (!allMatch) break
        }
        if (!allMatch) return null
        putJson(branchKey, legacy)
        remove(base)
        return legacy
    }

    fun savePayments(list: List<PaymentRecord>, branch: String? = null) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        putJson(keyFor("archive_payments", branch), arr)
    }

    fun loadMyPayments(): MutableList<PaymentRecord> {
        val out = mutableListOf<PaymentRecord>()
        getJsonArray("my_account_payments")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(PaymentRecord.from(it)) }
            }
        }
        return out
    }

    fun saveMyPayments(list: List<PaymentRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        putJson("my_account_payments", arr)
    }

    fun saveMyPeriods(list: List<Period>) {
        val arr = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            o.put("created_at", p.createdAt)
            o.put("closed_at", p.closedAt)
            val pays = JSONArray()
            p.payments.forEach { pays.put(it.toJson()) }
            o.put("payments", pays)
            arr.put(o)
        }
        putJson("my_account_periods", arr)
    }

    fun loadMyPeriods(): MutableList<Period> {
        val out = mutableListOf<Period>()
        getJsonArray("my_account_periods")?.let { arr ->
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

    fun loadPayments(branch: String? = null): MutableList<PaymentRecord> {
        val out = mutableListOf<PaymentRecord>()
        val arr = getJsonArray(keyFor("archive_payments", branch))
            ?: migrateLegacy("archive_payments", branch) ?: return out
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out.add(PaymentRecord.from(it)) }
        }
        return out
    }

    fun savePeriods(periods: List<Period>, branch: String? = null) {
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
        putJson(keyFor("archive_periods", branch), arr)
    }

    fun loadPeriods(branch: String? = null): MutableList<Period> {
        val out = mutableListOf<Period>()
        val arr = getJsonArray(keyFor("archive_periods", branch))
            ?: migrateLegacy("archive_periods", branch) ?: return out
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
        return out
    }
}
