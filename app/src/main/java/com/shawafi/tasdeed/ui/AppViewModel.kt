package com.shawafi.tasdeed.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shawafi.tasdeed.data.AppRepository
import com.shawafi.tasdeed.data.Branch
import com.shawafi.tasdeed.data.FreePayment
import com.shawafi.tasdeed.data.LocalStore
import com.shawafi.tasdeed.data.PaymentRecord
import com.shawafi.tasdeed.data.Period
import com.shawafi.tasdeed.data.Subscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToastMsg(val text: String, val isError: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LocalStore(app)
    val repo = AppRepository(store)

    val user = MutableStateFlow<String?>(null)
    val branchKey = MutableStateFlow<String?>(null)
    val branchName = MutableStateFlow("")
    val isLoggedIn = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<ToastMsg?>(null)
    val darkTheme = MutableStateFlow(store.getBool("theme", false))
    val uiFps = MutableStateFlow(store.getInt("ui_fps", 60))
    val fontScale = MutableStateFlow(store.getInt("font_scale", 100))
    val bioEnabled = MutableStateFlow(store.getBool("bio_enabled"))
    val devMode = MutableStateFlow(store.getBool("dev_mode", false))
    val nowTick = MutableStateFlow(System.currentTimeMillis())

    val subscribers = MutableStateFlow<List<Subscriber>>(emptyList())
    val locks = MutableStateFlow<Map<String, Long>>(emptyMap())

    val currentPayments = MutableStateFlow<MutableList<PaymentRecord>>(store.loadPayments())
    val periods = MutableStateFlow<MutableList<Period>>(store.loadPeriods())
    val myAccountPayments = MutableStateFlow<MutableList<PaymentRecord>>(store.loadMyPayments())
    val myPeriods = MutableStateFlow<MutableList<Period>>(store.loadMyPeriods())
    val pendingPayments = MutableStateFlow<MutableList<PaymentRecord>>(loadPendingList("pending_payments"))
    val pendingFreePayments = MutableStateFlow<MutableList<FreePayment>>(loadPendingList("pending_free_payments"))
    val freePayments = MutableStateFlow<List<FreePayment>>(emptyList())
    val isOnline = MutableStateFlow(true)

    private var lockJob: Job? = null
    private var frameJob: Job? = null
    private var netJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    private fun <T> loadPendingList(key: String): MutableList<T> {
        return when (key) {
            "pending_payments" -> {
                val l = mutableListOf<PaymentRecord>()
                store.getJsonArray(key)?.let { arr ->
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { l.add(PaymentRecord.from(it)) }
                }
                l as MutableList<T>
            }
            else -> {
                val l = mutableListOf<FreePayment>()
                store.getJsonArray(key)?.let { arr ->
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { l.add(FreePayment.from(it)) }
                }
                l as MutableList<T>
            }
        }
    }

    private fun savePendingList(key: String) {
        when (key) {
            "pending_payments" -> {
                val arr = org.json.JSONArray()
                pendingPayments.value.forEach { arr.put(it.toJson()) }
                store.putJson(key, arr)
            }
            else -> {
                val arr = org.json.JSONArray()
                pendingFreePayments.value.forEach { arr.put(it.toJson()) }
                store.putJson(key, arr)
            }
        }
    }

    init {
        // لا دخول تلقائي: دائماً تظهر شاشة تسجيل الدخول (ببيانات محفوظة إن وجدت)
        val savedUser = store.getString("saved_user")
        if (savedUser != null) {
            repo.loadFromCache()
        }
        startNetLoop()
    }

    private fun startNetLoop() {
        netJob?.cancel()
        netJob = viewModelScope.launch {
            while (true) {
                isOnline.value = hasNetwork()
                delay(3000)
            }
        }
    }

    fun setTheme(dark: Boolean) {
        store.putBool("theme", dark)
        darkTheme.value = dark
    }

    // وضع المطور: تفعيل محلي على الجهاز فقط، لا يُزامن سحابياً
    fun setDevMode(on: Boolean) {
        store.putBool("dev_mode", on)
        devMode.value = on
    }

    fun setUiFps(fps: Int) {
        store.putInt("ui_fps", fps)
        uiFps.value = fps
        startFrameTick()
    }

    fun setFontScale(percent: Int) {
        store.putInt("font_scale", percent)
        fontScale.value = percent
    }

    fun setBioEnabled(on: Boolean) {
        store.putBool("bio_enabled", on)
        bioEnabled.value = on
    }

    fun hasSavedCredentials(): Boolean =
        !store.getString("saved_user").isNullOrBlank() && !store.getString("saved_pass").isNullOrBlank()

    fun loginWithSavedCredentials() {
        val u = store.getString("saved_user") ?: return
        val p = store.getString("saved_pass") ?: return
        if (isLoggedIn.value) return
        login(u, p, remember = true, useNetwork = true)
    }

    private fun startFrameTick() {
        frameJob?.cancel()
        frameJob = viewModelScope.launch {
            val fps = uiFps.value.coerceIn(1, 120)
            while (true) {
                nowTick.value = System.currentTimeMillis()
                delay(1000L / fps)
            }
        }
    }

    fun toast(text: String, isError: Boolean = false) {
        message.value = ToastMsg(text, isError)
    }

    fun clearToast() {
        message.value = null
    }

    fun login(username: String, password: String, remember: Boolean, useNetwork: Boolean) {
        viewModelScope.launch {
            loading.value = true
            val result = withContext(Dispatchers.IO) {
                if (useNetwork) {
                    if (!hasNetwork()) {
                        return@withContext "no_net"
                    }
                    try {
                        repo.cacheAllData()
                        repo.login(username, password)
                    } catch (e: Exception) { null }
                } else {
                    repo.loadFromCache()
                    repo.login(username, password)
                }
            }
            loading.value = false
            when {
                result == "no_net" -> toast("الجهاز بدون نت! استخدم الدخول بدون نت", true)
                result == null -> toast("اسم المستخدم أو كلمة المرور خاطئة", true)
                else -> {
                    val (k, b) = result as Pair<String, Branch>
                    repo.setSession(k, username)
                    user.value = username
                    branchKey.value = k
                    branchName.value = b.name
                    isLoggedIn.value = true
                    if (remember) {
                        store.putString("saved_user", username)
                        store.putString("saved_pass", password)
                        store.putString("remember_me", "true")
                    } else {
                        store.remove("saved_user"); store.remove("saved_pass"); store.remove("remember_me")
                    }
                    reloadSubscribers()
                    startLocksLoop()
                    if (useNetwork) fetchFreePayments()
                    if (useNetwork) fetchArchiveFromCloud()
                    toast("مرحباً $username 👋")
                }
            }
        }
    }

    fun logout() {
        lockJob?.cancel()
        frameJob?.cancel()
        repo.setSession(null, null)
        isLoggedIn.value = false
        user.value = null
        branchKey.value = null
        branchName.value = ""
        locks.value = emptyMap()
    }

    private fun hasNetwork(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val info = cm.activeNetworkInfo ?: return false
        return info.isConnected
    }

    fun reloadSubscribers() {
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.fetchSubscribers() }
                subscribers.value = list.values.sortedBy { it.name }
            } catch (e: Exception) {
                // بدون إنترنت: نعرض المخبأ المحلي حتى لا ينهار التطبيق
                subscribers.value = repo.getSubscribers().values.sortedBy { it.name }
            }
        }
    }

    fun startLocksLoop() {
        lockJob?.cancel()
        // حلقة الفريمات: تحدّث nowTick بسلاسة حسب fps المختار
        startFrameTick()
        // حلقة الأقفال: جلب من النت كل 10 ثوانٍ فقط (لا نضرب الشبكة بالفريمات)
        lockJob = viewModelScope.launch {
            while (true) {
                withContext(Dispatchers.IO) {
                    try {
                        val fetched = repo.fetchLocks()
                        if (fetched.isNotEmpty() || locks.value.isNotEmpty()) locks.value = fetched
                    } catch (e: Exception) {}
                }
                delay(10000)
            }
        }
    }

    fun acquireLock(subKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.acquireLock(subKey)
            locks.value = repo.fetchLocks()
        }
    }

    fun releaseLock(subKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.releaseLock(subKey)
            locks.value = repo.fetchLocks()
        }
    }

    fun isLocked(subKey: String): Boolean = (locks.value[subKey] ?: 0) > System.currentTimeMillis()

    // ---------- payments ----------

    fun recordPayment(sub: Subscriber, amount: Double, note: String, periodIdx: Int?) {
        val rec = PaymentRecord(
            subscriberId = sub.id.ifEmpty { sub.key },
            subscriberName = sub.name,
            meterNumber = sub.meterNumber,
            amount = amount,
            unpaidBalance = sub.unpaidBalance,
            note = note,
            paymentDate = repo.currentDate(),
            branch = branchKey.value ?: "",
            branchName = branchName.value,
            collectorUser = user.value ?: "",
            createdAt = System.currentTimeMillis(),
            localId = "pay_" + System.currentTimeMillis() + "_" + randomSuffix(),
            periodIdx = periodIdx
        )
        pendingPayments.value = (pendingPayments.value + rec).toMutableList()
        savePendingList("pending_payments")
        saveToArchive(rec, periodIdx)
        toast("تم تسجيل ${amount} د.ع 💰")
        if (hasNetwork()) syncPendingPayments()
    }

    fun addFreePayment(beneficiary: String, amount: Double, note: String) {
        val rec = FreePayment(
            amount = amount,
            beneficiary = beneficiary,
            note = note,
            paymentDate = repo.currentDate(),
            branch = branchKey.value ?: "",
            branchName = branchName.value,
            collectorUser = user.value ?: "",
            createdAt = System.currentTimeMillis(),
            localId = "fp_" + System.currentTimeMillis() + "_" + randomSuffix()
        )
        pendingFreePayments.value = (pendingFreePayments.value + rec).toMutableList()
        savePendingList("pending_free_payments")
        toast("تم حفظ الدفعة الحرة 💸")
        if (hasNetwork()) syncFreePayments()
    }

    // ---------- حساباتي (كشف شخصي) ----------

    fun addMyAccountPayment(amount: Double, note: String) {
        val rec = PaymentRecord(
            subscriberName = "دفعة",
            meterNumber = "",
            amount = amount,
            note = note,
            paymentDate = repo.currentDate(),
            branch = branchKey.value ?: "",
            branchName = branchName.value,
            collectorUser = user.value ?: "",
            createdAt = System.currentTimeMillis(),
            localId = "my_" + System.currentTimeMillis() + "_" + randomSuffix()
        )
        myAccountPayments.value = (myAccountPayments.value + rec).toMutableList()
        store.saveMyPayments(myAccountPayments.value)
        toast("✅ تم حفظ الدفعة في حساباتي")
    }

    fun reloadMyAccount() {
        myAccountPayments.value = store.loadMyPayments()
        myPeriods.value = store.loadMyPeriods()
    }

    fun newMyPeriod(name: String) {
        val old = myAccountPayments.value
        val p = Period(
            name = name,
            payments = old.toMutableList(),
            createdAt = repo.currentDate(),
            closedAt = System.currentTimeMillis()
        )
        myPeriods.value = (myPeriods.value + p).toMutableList()
        myAccountPayments.value = mutableListOf()
        store.saveMyPayments(myAccountPayments.value)
        store.saveMyPeriods(myPeriods.value)
        toast("✅ تم فتح كشف حساباتي جديد: $name")
    }

    fun deleteMyPeriod(idx: Int) {
        if (idx in myPeriods.value.indices) {
            val list = myPeriods.value.toMutableList()
            list.removeAt(idx)
            myPeriods.value = list
            store.saveMyPeriods(list)
            toast("تم حذف الكشف من حساباتي")
        }
    }

    fun renameMyPeriod(idx: Int, newName: String) {
        if (idx in myPeriods.value.indices && newName.isNotBlank()) {
            val list = myPeriods.value.toMutableList()
            list[idx] = list[idx].copy(name = newName.trim())
            myPeriods.value = list
            store.saveMyPeriods(list)
            toast("✅ تم تعديل الاسم")
        }
    }

    fun saveEditedMyPeriod(isCurrent: Boolean, idx: Int, editedIds: Set<String>, newAmounts: Map<String, Double>) {
        val list = if (isCurrent) myAccountPayments.value else myPeriods.value.getOrNull(idx)?.payments?.toMutableList() ?: return
        val newList = mutableListOf<PaymentRecord>()
        list.forEach { pay ->
            if (pay.localId in editedIds) {
                val amt = newAmounts[pay.localId] ?: pay.amount
                if (amt > 0) newList.add(pay.copy(amount = amt))
            } else {
                newList.add(pay)
            }
        }
        if (isCurrent) {
            myAccountPayments.value = newList.toMutableList()
            store.saveMyPayments(myAccountPayments.value)
        } else if (idx in myPeriods.value.indices) {
            myPeriods.value[idx].payments.clear()
            myPeriods.value[idx].payments.addAll(newList)
            store.saveMyPeriods(myPeriods.value)
        }
        toast("✅ تم حفظ التعديلات")
    }

    fun syncPendingPayments() {
        viewModelScope.launch {
            val pending = pendingPayments.value.toMutableList()
            if (pending.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val rec = iterator.next()
                    if (repo.pushPendingPayment(rec)) iterator.remove()
                }
            }
            pendingPayments.value = pending
            savePendingList("pending_payments")
        }
    }

    fun syncFreePayments() {
        viewModelScope.launch {
            val pending = pendingFreePayments.value.toMutableList()
            if (pending.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val rec = iterator.next()
                    if (repo.pushFreePayment(rec)) iterator.remove()
                }
            }
            pendingFreePayments.value = pending
            savePendingList("pending_free_payments")
        }
    }

    fun fetchFreePayments() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { freePayments.value = repo.fetchFreePayments() } catch (e: Exception) {}
            }
        }
    }

    fun editPendingPayment(idx: Int, amount: Double, note: String) {
        val list = pendingPayments.value.toMutableList()
        if (idx < 0 || idx >= list.size) return
        list[idx] = list[idx].copy(amount = amount, note = note)
        pendingPayments.value = list
        savePendingList("pending_payments")
    }

    fun deletePendingPayment(idx: Int) {
        val list = pendingPayments.value.toMutableList()
        if (idx < 0 || idx >= list.size) return
        list.removeAt(idx)
        pendingPayments.value = list
        savePendingList("pending_payments")
    }

    // ---------- archive ----------

    private fun pushArchiveCloud() {
        val bk = branchKey.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { repo.pushArchive(bk, currentPayments.value, periods.value) } catch (e: Exception) {}
            }
        }
    }

    fun fetchArchiveFromCloud() {
        val bk = branchKey.value ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                try { repo.fetchArchive(bk) } catch (e: Exception) { null }
            } ?: return@launch
            val (cloudCur, cloudPeriods) = res
            var changed = false
            val curIds = currentPayments.value.map { it.localId }.toSet()
            val extra = cloudCur.filter { it.localId !in curIds }
            if (extra.isNotEmpty()) {
                currentPayments.value = (currentPayments.value + extra).toMutableList()
                changed = true
            }
            val lp = periods.value.toMutableList()
            cloudPeriods.forEach { cp ->
                val idx = lp.indexOfFirst { it.name == cp.name && it.createdAt == cp.createdAt }
                if (idx >= 0) {
                    val ids = lp[idx].payments.map { it.localId }.toSet()
                    val add = cp.payments.filter { it.localId !in ids }
                    if (add.isNotEmpty()) {
                        lp[idx].payments.addAll(add)
                        changed = true
                    }
                } else {
                    lp.add(cp)
                    changed = true
                }
            }
            if (changed) {
                periods.value = lp
                store.savePayments(currentPayments.value)
                store.savePeriods(periods.value)
                withContext(Dispatchers.IO) {
                    try { repo.pushArchive(bk, currentPayments.value, periods.value) } catch (e: Exception) {}
                }
                toast("تم تحديث الكشوفات من السحابة ✅")
            } else {
                withContext(Dispatchers.IO) {
                    try { repo.pushArchive(bk, currentPayments.value, periods.value) } catch (e: Exception) {}
                }
            }
        }
    }

    private fun saveToArchive(record: PaymentRecord, periodIdx: Int?) {
        val idx = record.periodIdx ?: periodIdx
        if (idx != null && idx >= 0 && idx < periods.value.size) {
            periods.value[idx].payments.add(record)
            store.savePeriods(periods.value)
            pushArchiveCloud()
            return
        }
        currentPayments.value = (currentPayments.value + record).toMutableList()
        store.savePayments(currentPayments.value)
        pushArchiveCloud()
    }

    fun newPeriod(name: String) {
        val old = currentPayments.value
        val p = Period(
            name = name,
            payments = old.toMutableList(),
            createdAt = repo.currentDate(),
            closedAt = System.currentTimeMillis()
        )
        periods.value = (periods.value + p).toMutableList()
        currentPayments.value = mutableListOf()
        store.savePayments(currentPayments.value)
        store.savePeriods(periods.value)
        pushArchiveCloud()
        toast("✅ تم فتح كشف جديد: $name")
    }

    fun savePeriodData(isCurrent: Boolean, idx: Int, list: List<PaymentRecord>) {
        if (isCurrent) {
            currentPayments.value = list.toMutableList()
            store.savePayments(currentPayments.value)
        } else if (idx in periods.value.indices) {
            periods.value[idx].payments.clear()
            periods.value[idx].payments.addAll(list)
            store.savePeriods(periods.value)
        }
        pushArchiveCloud()
    }

    fun deletePeriod(idx: Int) {
        if (idx in periods.value.indices) {
            val list = periods.value.toMutableList()
            list.removeAt(idx)
            periods.value = list
            store.savePeriods(list)
            pushArchiveCloud()
            toast("تم حذف الكشف")
        }
    }

    fun renamePeriod(idx: Int, newName: String) {
        if (idx in periods.value.indices && newName.isNotBlank()) {
            val list = periods.value.toMutableList()
            list[idx] = list[idx].copy(name = newName.trim())
            periods.value = list
            store.savePeriods(list)
            pushArchiveCloud()
            toast("✅ تم تعديل الاسم")
        }
    }

    fun saveEditedPeriod(isCurrent: Boolean, idx: Int, editedIds: Set<String>, newAmounts: Map<String, Double>) {
        val list = if (isCurrent) currentPayments.value else periods.value.getOrNull(idx)?.payments?.toMutableList() ?: return
        val newList = mutableListOf<PaymentRecord>()
        list.forEach { pay ->
            if (pay.localId in editedIds) {
                val amt = newAmounts[pay.localId] ?: pay.amount
                if (amt > 0) newList.add(pay.copy(amount = amt))
            } else {
                newList.add(pay)
            }
        }
        savePeriodData(isCurrent, idx, newList)
        toast("✅ تم حفظ التعديلات")
    }

    private fun randomSuffix(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
