package com.shawafi.tasdeed.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AppRepository(val store: LocalStore) {

    private var branchesCache: MutableMap<String, Branch> = mutableMapOf()
    private var subscribersCache: MutableMap<String, Subscriber> = mutableMapOf()

    val currentBranch: String? get() = store.getString("current_branch")
    val currentUser: String? get() = store.getString("current_user")

    fun setSession(branchKey: String?, username: String?) {
        if (branchKey == null || username == null) {
            store.remove("current_branch")
            store.remove("current_user")
        } else {
            store.putString("current_branch", branchKey)
            store.putString("current_user", username)
        }
    }

    // ---------- cache ----------

    private fun saveLocalBranches(map: Map<String, Branch>) {
        val o = JSONObject()
        map.forEach { (k, v) ->
            o.put(k, JSONObject().put("name", v.name).put("username", v.username).put("password", v.password))
        }
        store.putJson("branches", o)
    }

    private fun saveLocalSubscribers(map: Map<String, Subscriber>) {
        val o = JSONObject()
        map.forEach { (k, v) ->
            o.put(k, JSONObject()
                .put("id", v.id).put("name", v.name).put("meter_number", v.meterNumber)
                .put("subscriber_number", v.subscriberNumber)
                .put("unpaid_balance", v.unpaidBalance).put("last_amount", v.lastAmount)
                .put("is_active", if (v.isActive) 1 else 0).put("sync_key", v.syncKey)
                .put("hidden", if (v.hidden) 1 else 0)
                .put("hide_amounts", if (v.hideAmounts) 1 else 0))
        }
        store.putJson("subscribers", o)
    }

    fun loadFromCache() {
        branchesCache = mutableMapOf()
        store.getJsonObject("branches")?.let { o ->
            o.keys().forEach { k -> o.optJSONObject(k)?.let { Branch.from(k, it)?.let { b -> branchesCache[k] = b } } }
        }
        subscribersCache = mutableMapOf()
        store.getJsonObject("subscribers")?.let { o ->
            o.keys().forEach { k -> o.optJSONObject(k)?.let { Subscriber.from(k, it)?.let { s -> subscribersCache[k] = s } } }
        }
        subscribersCache = filterSubscribers(subscribersCache)
    }

    fun loadAppConfig(): AppConfig {
        val o = store.getJsonObject("app_config")
        return AppConfig.from(o)
    }

    // ---------- firebase ----------

    private fun filterBySyncKey(map: Map<String, Branch>): Map<String, Branch> =
        map.filterValues { it.syncKey == FirebaseClient.SYNC_KEY }

    private fun filterSubscribers(map: Map<String, Subscriber>): MutableMap<String, Subscriber> {
        val out = mutableMapOf<String, Subscriber>()
        val seen = mutableMapOf<String, String>()
        map.forEach { (k, s) ->
            if (!s.isActive) return@forEach
            val key = s.name + "|" + s.meterNumber
            val existingKey = seen[key]
            if (existingKey != null) {
                val existing = out[existingKey]
                if (existing != null && (s.id.toIntOrNull() ?: 0) > (existing.id.toIntOrNull() ?: 0)) {
                    out[existingKey] = s
                }
            } else {
                out[k] = s
                seen[key] = k
            }
        }
        return out
    }

    fun fetchBranches(): Map<String, Branch> {
        val root = FirebaseClient.get("${FirebaseClient.ROOT}/branches") ?: return branchesCache
        val map = mutableMapOf<String, Branch>()
        root.keys().forEach { k ->
            root.optJSONObject(k)?.let { Branch.from(k, it) }?.let { map[k] = it }
        }
        branchesCache = filterBySyncKey(map).toMutableMap()
        saveLocalBranches(branchesCache)
        return branchesCache
    }

    fun fetchSubscribers(): MutableMap<String, Subscriber> {
        val raw = FirebaseClient.getRaw("${FirebaseClient.ROOT}/subscribers") ?: return subscribersCache
        // العقدة فارغة أو محذوفة من السحابة → المشتركون صفر، امسح المخبأ القديم
        if (raw.isEmpty() || raw == "null" || raw == "{}") {
            if (subscribersCache.isNotEmpty()) {
                subscribersCache = mutableMapOf()
                saveLocalSubscribers(subscribersCache)
            }
            return subscribersCache
        }
        val root = try { JSONObject(raw) } catch (e: Exception) { return subscribersCache }
        val map = mutableMapOf<String, Subscriber>()
        root.keys().forEach { k ->
            root.optJSONObject(k)?.let { Subscriber.from(k, it) }?.let { map[k] = it }
        }
        subscribersCache = filterSubscribers(map.filterValues { it.syncKey == FirebaseClient.SYNC_KEY })
        saveLocalSubscribers(subscribersCache)
        return subscribersCache
    }

    fun fetchAppConfig(): AppConfig {
        val o = FirebaseClient.get("${FirebaseClient.ROOT}/app_config")
        o?.let { store.putJson("app_config", it) }
        return AppConfig.from(o ?: store.getJsonObject("app_config"))
    }

    /** تحديث علامات الرؤية للمشترك (إخفاء عن الفروع / إخفاء المبالغ) في السحابة والمخبأ المحلي */
    fun updateSubscriberVisibility(key: String, hidden: Boolean?, hideAmounts: Boolean?): Boolean {
        val sub = subscribersCache[key] ?: return false
        val payload = JSONObject()
        hidden?.let { payload.put("hidden", if (it) 1 else 0) }
        hideAmounts?.let { payload.put("hide_amounts", if (it) 1 else 0) }
        if (payload.length() == 0) return false
        val ok = try {
            FirebaseClient.update("${FirebaseClient.ROOT}/subscribers/$key", payload)
        } catch (e: Exception) { false }
        if (ok) {
            subscribersCache[key] = sub.copy(
                hidden = hidden ?: sub.hidden,
                hideAmounts = hideAmounts ?: sub.hideAmounts
            )
            saveLocalSubscribers(subscribersCache)
        }
        return ok
    }

    /** كل المشتركين بما فيهم المخفيين (لشاشة إدارة المشتركين) */
    fun getAllSubscribers(): List<Subscriber> = subscribersCache.values.toList()

    suspend fun cacheAllData() = withContext(Dispatchers.IO) {
        fetchBranches()
        fetchSubscribers()
        fetchAppConfig()
        store.putString("last_cache_time", System.currentTimeMillis().toString())
    }

    fun getBranches(): Map<String, Branch> = branchesCache
    fun getSubscribers(): Map<String, Subscriber> = subscribersCache

    fun login(username: String, password: String): Pair<String, Branch>? {
        branchesCache.forEach { (k, b) ->
            if (b.username == username && b.password == password) return k to b
        }
        return null
    }

    // ---------- locks ----------

    fun fetchLocks(): Map<String, Long> {
        val root = FirebaseClient.get("${FirebaseClient.ROOT}/locks") ?: return emptyMap()
        val now = System.currentTimeMillis()
        val out = mutableMapOf<String, Long>()
        root.keys().forEach { k ->
            val expires = root.optJSONObject(k)?.optLong("expires_at", 0) ?: 0
            if (expires > now) out[k] = expires
        }
        return out
    }

    fun acquireLock(subKey: String) {
        val payload = JSONObject()
        payload.put("$subKey/expires_at", System.currentTimeMillis() + FirebaseClient.LOCK_DURATION)
        FirebaseClient.update("${FirebaseClient.ROOT}/locks", payload)
    }

    fun releaseLock(subKey: String) {
        FirebaseClient.delete("${FirebaseClient.ROOT}/locks/$subKey")
    }

    // ---------- payments ----------

    fun pushPendingPayment(record: PaymentRecord): Boolean {
        // منع التكرار: إذا وُجدت نفس الدفعة (نفس local_id) في السحابة مسبقاً
        // (رفع سابق نجح لكن انقطع الرد)، نعتبرها مرفوعة ولا نرفع نسخة جديدة
        if (record.localId.isNotEmpty()) {
            try {
                val root = FirebaseClient.get("${FirebaseClient.ROOT}/pending_payments")
                if (root != null) {
                    val it = root.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        val v = root.optJSONObject(k)
                        if (v != null && v.optString("local_id") == record.localId) {
                            bumpCacheVersion()
                            return true
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        val key = FirebaseClient.post("${FirebaseClient.ROOT}/pending_payments", record.toJson()) ?: return false
        bumpCacheVersion()
        return key.length() > 0
    }

    fun pushFreePayment(record: FreePayment): Boolean {
        // منع التكرار: نفس منطق الدفعات — نتحقق من local_id أولاً
        if (record.localId.isNotEmpty()) {
            try {
                val root = FirebaseClient.get("${FirebaseClient.ROOT}/free_payments")
                if (root != null) {
                    val it = root.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        val v = root.optJSONObject(k)
                        if (v != null && v.optString("local_id") == record.localId) {
                            bumpCacheVersion()
                            return true
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        val key = FirebaseClient.post("${FirebaseClient.ROOT}/free_payments", record.toJson()) ?: return false
        bumpCacheVersion()
        return key.length() > 0
    }

    fun fetchFreePayments(): List<FreePayment> {
        val root = FirebaseClient.get("${FirebaseClient.ROOT}/free_payments") ?: return emptyList()
        val out = mutableListOf<FreePayment>()
        root.keys().forEach { k ->
            root.optJSONObject(k)?.let { FreePayment.from(it) }?.let {
                if (it.syncKey == FirebaseClient.SYNC_KEY) out.add(it)
            }
        }
        return out.sortedByDescending { it.createdAt }
    }

    fun fetchArchive(branch: String): Pair<MutableList<PaymentRecord>, MutableList<Period>>? {
        val o = FirebaseClient.get("${FirebaseClient.ROOT}/archive/$branch") ?: return null
        val cur = mutableListOf<PaymentRecord>()
        o.optJSONArray("current")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { cur.add(PaymentRecord.from(it)) }
        }
        val ps = mutableListOf<Period>()
        o.optJSONArray("periods")?.let { arr ->
            for (i in 0 until arr.length()) {
                val po = arr.optJSONObject(i) ?: continue
                val p = Period(
                    name = po.optString("name"),
                    createdAt = po.optString("created_at"),
                    closedAt = po.optLong("closed_at", System.currentTimeMillis())
                )
                po.optJSONArray("payments")?.let { pays ->
                    for (j in 0 until pays.length()) pays.optJSONObject(j)?.let { p.payments.add(PaymentRecord.from(it)) }
                }
                ps.add(p)
            }
        }
        return cur to ps
    }

    fun pushArchive(branch: String, current: List<PaymentRecord>, periodsList: List<Period>): Boolean {
        val payload = JSONObject()
        val cur = JSONArray()
        current.forEach { cur.put(it.toJson()) }
        payload.put("current", cur)
        val ps = JSONArray()
        periodsList.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            o.put("created_at", p.createdAt)
            o.put("closed_at", p.closedAt)
            val pays = JSONArray()
            p.payments.forEach { pays.put(it.toJson()) }
            o.put("payments", pays)
            ps.put(o)
        }
        payload.put("periods", ps)
        payload.put("updated_at", System.currentTimeMillis())
        return FirebaseClient.put("${FirebaseClient.ROOT}/archive/$branch", payload)
    }

    private fun bumpCacheVersion() {
        val payload = JSONObject()
        payload.put("cache_version", System.currentTimeMillis())
        FirebaseClient.update("${FirebaseClient.ROOT}/stats", payload)
    }

    fun currentDate(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d/%02d/%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
