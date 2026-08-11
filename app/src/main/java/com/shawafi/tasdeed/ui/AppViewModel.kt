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

    val subscribers = MutableStateFlow<List<Subscriber>>(emptyList())
    val locks = MutableStateFlow<Map<String, Long>>(emptyMap())

    val currentPayments = MutableStateFlow<MutableList<PaymentRecord>>(store.loadPayments())
    val periods = MutableStateFlow<MutableList<Period>>(store.loadPeriods())
    val pendingPayments = MutableStateFlow<MutableList<PaymentRecord>>(loadPendingList("pending_payments"))
    val pendingFreePayments = MutableStateFlow<MutableList<FreePayment>>(loadPendingList("pending_free_payments"))
    val freePayments = MutableStateFlow<List<FreePayment>>(emptyList())

    private var lockJob: Job? = null

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
        val savedUser = store.getString("saved_user")
        if (savedUser != null && store.getString("remember_me") == "true") {
            user.value = savedUser
            repo.loadFromCache()
            if (store.getString("current_branch") != null) {
                branchKey.value = store.getString("current_branch")
                isLoggedIn.value = true
                branchName.value = repo.getBranches()[branchKey.value]?.name ?: ""
                reloadSubscribers()
                startLocksLoop()
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
                    toast("مرحباً $username 👋")
                }
            }
        }
    }

    fun logout() {
        lockJob?.cancel()
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
            withContext(Dispatchers.IO) { repo.fetchSubscribers() }
            subscribers.value = repo.getSubscribers().values.sortedBy { it.name }
        }
    }

    fun startLocksLoop() {
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            while (true) {
                withContext(Dispatchers.IO) {
                    try { locks.value = repo.fetchLocks() } catch (e: Exception) {}
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

    private fun saveToArchive(record: PaymentRecord, periodIdx: Int?) {
        val idx = record.periodIdx ?: periodIdx
        if (idx != null && idx >= 0 && idx < periods.value.size) {
            periods.value[idx].payments.add(record)
            store.savePeriods(periods.value)
            return
        }
        currentPayments.value = (currentPayments.value + record).toMutableList()
        store.savePayments(currentPayments.value)
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
    }

    fun deletePeriod(idx: Int) {
        if (idx in periods.value.indices) {
            val list = periods.value.toMutableList()
            list.removeAt(idx)
            periods.value = list
            store.savePeriods(list)
            toast("تم حذف الكشف")
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
