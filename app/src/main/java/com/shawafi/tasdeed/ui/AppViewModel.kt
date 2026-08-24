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
    val mergeOps = MutableStateFlow(store.getBool("merge_ops", false))
    val nowTick = MutableStateFlow(System.currentTimeMillis())

    val subscribers = MutableStateFlow<List<Subscriber>>(emptyList())
    // كل المشتركين غير المفلترين (يُستخدم في شاشة إدارة المشتركين بوضع المطور)
    val allSubscribers = MutableStateFlow<List<Subscriber>>(emptyList())
    val locks = MutableStateFlow<Map<String, Long>>(emptyMap())

    val currentPayments = MutableStateFlow<MutableList<PaymentRecord>>(store.loadPayments())
    val periods = MutableStateFlow<MutableList<Period>>(store.loadPeriods())
    val myAccountPayments = MutableStateFlow<MutableList<PaymentRecord>>(store.loadMyPayments())
    val myPeriods = MutableStateFlow<MutableList<Period>>(store.loadMyPeriods())
    val pendingPayments = MutableStateFlow<MutableList<PaymentRecord>>(loadPendingList("pending_payments"))
    val pendingFreePayments = MutableStateFlow<MutableList<FreePayment>>(loadPendingList("pending_free_payments"))
    val freePayments = MutableStateFlow<List<FreePayment>>(emptyList())
    val isOnline = MutableStateFlow(true)
    // مفاتيح الكشوف المحذوفة (سحابياً) لمنع عودتها لأي جهاز: "name|createdAt"
    val deletedKeys = MutableStateFlow<Set<String>>(emptySet())

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
        // عند التفعيل يظهر المشتركون المخفيون، وعند الإيقاف يختفون
        applySubscriberFilter()
    }

    // دمج العمليات: عندما يكون مفعلاً تندمج دفعات نفس المشترك في صف واحد
    fun setMergeOps(on: Boolean) {
        store.putBool("merge_ops", on)
        mergeOps.value = on
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
        viewModelScope.launch {
            loading.value = true
            var result: Pair<String, Branch>? = null
            var usedNetwork = true
            withContext(Dispatchers.IO) {
                if (hasNetwork()) {
                    try {
                        repo.cacheAllData()
                        result = repo.login(u, p)
                    } catch (e: Exception) {}
                }
                if (result == null) {
                    // دخول محلي من المخبأ (يعمل بدون إنترنت أيضاً)
                    usedNetwork = false
                    try {
                        repo.loadFromCache()
                        result = repo.login(u, p)
                    } catch (e: Exception) {}
                }
            }
            loading.value = false
            if (result == null) {
                toast("فشل الدخول بالبصمة — تحقق من بياناتك أو من النت", true)
            } else {
                val (k, b) = result as Pair<String, Branch>
                repo.setSession(k, u)
                user.value = u
                branchKey.value = k
                branchName.value = b.name
                isLoggedIn.value = true
                reloadSubscribers()
                loadBranchArchive(k)
                startLocksLoop()
                if (usedNetwork) {
                    fetchFreePayments()
                    fetchArchiveFromCloud()
                }
                toast("مرحباً $u 👋")
            }
        }
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

    // حفظ الأرشيف المحلي بمفتاح خاص لكل فرع (يمنع خلط كشوف الفروع مع بعضها)
    private fun saveBranchArchive() {
        store.savePeriods(periods.value, branchKey.value)
        store.savePayments(currentPayments.value, branchKey.value)
    }

    // تحميل كشوفات الفرع الحالي من المخزن المحلي (يستبدل قوائم الفرع السابق)
    private fun loadBranchArchive(branch: String) {
        currentPayments.value = store.loadPayments(branch).toMutableList()
        periods.value = store.loadPeriods(branch).toMutableList()
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
                    loadBranchArchive(k)
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
                allSubscribers.value = list.values.sortedBy { it.name }
                applySubscriberFilter()
            } catch (e: Exception) {
                // بدون إنترنت: نعرض المخبأ المحلي حتى لا ينهار التطبيق
                allSubscribers.value = repo.getSubscribers().values.sortedBy { it.name }
                applySubscriberFilter()
            }
        }
    }

    /** فلترة المشتركين: المخفيون (hidden) يظهرون فقط لجهاز المطور */
    private fun applySubscriberFilter() {
        subscribers.value = if (devMode.value) allSubscribers.value
            else allSubscribers.value.filterNot { it.hidden }
    }

    fun setSubscriberHidden(key: String, hidden: Boolean) {
        viewModelScope.launch {
            val sub = allSubscribers.value.firstOrNull { it.key == key } ?: return@launch
            // تحديث فوري محلياً ثم مزامنة السحابة
            allSubscribers.value = allSubscribers.value.map {
                if (it.key == key) it.copy(hidden = hidden) else it
            }
            applySubscriberFilter()
            val ok = withContext(Dispatchers.IO) { repo.updateSubscriberVisibility(key, hidden, null) }
            if (ok) {
                toast(if (hidden) "🙈 تم إخفاء المشترك عن الفروع الأخرى" else "👁️ أصبح المشترك ظاهراً للجميع")
            } else {
                toast("تعذرت المزامنة مع السحابة (تحقق من النت)", true)
            }
        }
    }

    fun setSubscriberHideAmounts(key: String, hideAmounts: Boolean) {
        viewModelScope.launch {
            val sub = allSubscribers.value.firstOrNull { it.key == key } ?: return@launch
            allSubscribers.value = allSubscribers.value.map {
                if (it.key == key) it.copy(hideAmounts = hideAmounts) else it
            }
            applySubscriberFilter()
            val ok = withContext(Dispatchers.IO) { repo.updateSubscriberVisibility(key, null, hideAmounts) }
            if (ok) {
                toast(if (hideAmounts) "🔒 تم إخفاء المبالغ عن كل الفروع" else "💰 المبالغ أصبحت ظاهرة للجميع")
            } else {
                toast("تعذرت المزامنة مع السحابة (تحقق من النت)", true)
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
                // عند عودة الإنترنت نرفع الدفعات المحفوظة تلقائياً
                if (hasNetwork() && (pendingPayments.value.isNotEmpty() || pendingFreePayments.value.isNotEmpty())) {
                    syncPendingPayments()
                    syncFreePayments()
                }
                delay(10000)
            }
        }
    }

    fun acquireLock(subKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.acquireLock(subKey)
                locks.value = repo.fetchLocks()
            } catch (e: Exception) {}
        }
    }

    fun releaseLock(subKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.releaseLock(subKey)
                locks.value = repo.fetchLocks()
            } catch (e: Exception) {}
        }
    }

    fun isLocked(subKey: String): Boolean = (locks.value[subKey] ?: 0) > System.currentTimeMillis()

    // ---------- payments ----------

    fun recordPayment(sub: Subscriber, amount: Double, note: String, periodIdx: Int) {
        if (periodIdx !in periods.value.indices) {
            toast("⚠️ أنشئ كشفاً أولاً من شاشة الكشوفات", true)
            return
        }
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
        saveToArchive(rec)
        // تذكّر آخر كشف سُدّد فيه هذا المشترك (محلياً على هذا الجهاز)
        val stmtName = periods.value[periodIdx].name
        store.putString("last_stmt_${sub.key}", stmtName)
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
        if (myPeriods.value.isEmpty()) {
            toast("⚠️ أنشئ كشفاً أولاً من شاشة حساباتي", true)
            return
        }
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
        val idx = myPeriods.value.lastIndex
        myPeriods.value[idx].payments.add(rec)
        store.saveMyPeriods(myPeriods.value)
        toast("✅ تم حفظ الدفعة في حساباتي")
    }

    fun reloadMyAccount() {
        myAccountPayments.value = store.loadMyPayments()
        myPeriods.value = store.loadMyPeriods()
    }

    fun newMyPeriod(name: String) {
        val p = Period(
            name = name,
            payments = mutableListOf(),
            createdAt = repo.currentDate(),
            closedAt = System.currentTimeMillis()
        )
        myPeriods.value = (myPeriods.value + p).toMutableList()
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

    fun saveEditedMyPeriod(idx: Int, editedIds: Set<String>, newAmounts: Map<String, Double>) {
        if (idx !in myPeriods.value.indices) return
        val list = myPeriods.value[idx].payments
        val newList = mutableListOf<PaymentRecord>()
        list.forEach { pay ->
            if (pay.localId in editedIds) {
                val amt = newAmounts[pay.localId] ?: pay.amount
                newList.add(pay.copy(amount = if (amt > 0) amt else pay.amount))
            } else {
                newList.add(pay)
            }
        }
        val lp = myPeriods.value.toMutableList()
        lp[idx] = lp[idx].copy(payments = newList)
        myPeriods.value = lp
        store.saveMyPeriods(lp)
        toast("✅ تم حفظ التعديلات")
    }

    fun deletePaymentsFromStatement(idx: Int, isMy: Boolean, ids: Set<String>) {
        if (ids.isEmpty()) return
        if (isMy) {
            if (idx in myPeriods.value.indices) {
                val kept = myPeriods.value[idx].payments.filterNot { it.localId in ids }
                val lp = myPeriods.value.toMutableList()
                lp[idx] = lp[idx].copy(payments = kept.toMutableList())
                myPeriods.value = lp
                store.saveMyPeriods(lp)
            }
        } else {
            if (idx in periods.value.indices) {
                val kept = periods.value[idx].payments.filterNot { it.localId in ids }
                val lp = periods.value.toMutableList()
                lp[idx] = lp[idx].copy(payments = kept.toMutableList())
                periods.value = lp
                saveBranchArchive()
                pushArchiveCloud()
            }
        }
        toast("🗑️ تم حذف الدفعات")
    }

    fun syncPendingPayments() {
        viewModelScope.launch {
            val pending = pendingPayments.value.toMutableList()
            if (pending.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val rec = iterator.next()
                    try {
                        if (repo.pushPendingPayment(rec)) iterator.remove() else break
                    } catch (e: Exception) { break }
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
                    try {
                        if (repo.pushFreePayment(rec)) iterator.remove() else break
                    } catch (e: Exception) { break }
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

    fun pushArchiveFor(branch: String, current: List<PaymentRecord>, periodsList: List<Period>) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { repo.pushArchive(branch, current, periodsList) } catch (e: Exception) { false }
            }
            if (ok) toast("✅ تم الحفظ وسيظهر التعديل عند الجميع")
            else toast("❌ فشل الرفع للسحابة (تحقق من النت)", true)
        }
    }

    fun fetchArchiveFromCloud() {
        val bk = branchKey.value ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                try { repo.fetchArchive(bk) } catch (e: Exception) { null }
            } ?: return@launch
            val deleted = withContext(Dispatchers.IO) {
                try { repo.fetchArchiveDeleted(bk) } catch (e: Exception) { emptySet() }
            }
            deletedKeys.value = deleted
            val (cloudCur, cloudPeriods) = res
            var changed = false
            val curIds = currentPayments.value.map { it.localId }.toSet()
            val extra = cloudCur.filter { it.localId !in curIds }
            if (extra.isNotEmpty()) {
                currentPayments.value = (currentPayments.value + extra).toMutableList()
                changed = true
            }
            val lp = periods.value.toMutableList()
            // إزالة الكشوف المحذوفة سحابياً من المحلي (حتى لا تعود لأي جهاز)
            val beforeDel = lp.size
            lp.removeAll { "${it.name}|${it.createdAt}" in deleted }
            if (lp.size != beforeDel) changed = true
            cloudPeriods.forEach { cp ->
                if ("${cp.name}|${cp.createdAt}" in deleted) return@forEach
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
                saveBranchArchive()
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

    private fun saveToArchive(record: PaymentRecord) {
        val idx = record.periodIdx
        if (idx != null && idx in periods.value.indices) {
            periods.value[idx].payments.add(record)
            saveBranchArchive()
            pushArchiveCloud()
        }
    }

    fun newPeriod(name: String) {
        val p = Period(
            name = name,
            payments = mutableListOf(),
            createdAt = repo.currentDate(),
            closedAt = System.currentTimeMillis()
        )
        periods.value = (periods.value + p).toMutableList()
        saveBranchArchive()
        pushArchiveCloud()
        toast("✅ تم فتح كشف جديد: $name")
    }

    fun savePeriodData(idx: Int, list: List<PaymentRecord>) {
        if (idx in periods.value.indices) {
            val lp = periods.value.toMutableList()
            lp[idx] = lp[idx].copy(payments = list.toMutableList())
            periods.value = lp
            saveBranchArchive()
        }
        pushArchiveCloud()
    }

    fun deletePeriod(idx: Int) {
        if (idx in periods.value.indices) {
            val target = periods.value[idx]
            val key = "${target.name}|${target.createdAt}"
            val list = periods.value.toMutableList()
            list.removeAt(idx)
            periods.value = list
            store.savePeriods(list, branchKey.value)
            pushArchiveCloud()
            deletedKeys.value = deletedKeys.value + key
            val bk = branchKey.value
            if (bk != null) {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        try { repo.pushArchiveDeleted(bk, deletedKeys.value.toList()) } catch (e: Exception) {}
                    }
                }
            }
            toast("تم حذف الكشف من السحابة أيضاً")
        }
    }

    fun renamePeriod(idx: Int, newName: String) {
        if (idx in periods.value.indices && newName.isNotBlank()) {
            val list = periods.value.toMutableList()
            list[idx] = list[idx].copy(name = newName.trim())
            periods.value = list
            store.savePeriods(list, branchKey.value)
            pushArchiveCloud()
            toast("✅ تم تعديل الاسم")
        }
    }

    fun saveEditedPeriod(idx: Int, editedIds: Set<String>, newAmounts: Map<String, Double>) {
        if (idx !in periods.value.indices) return
        val list = periods.value[idx].payments
        val newList = mutableListOf<PaymentRecord>()
        list.forEach { pay ->
            if (pay.localId in editedIds) {
                val amt = newAmounts[pay.localId] ?: pay.amount
                newList.add(pay.copy(amount = if (amt > 0) amt else pay.amount))
            } else {
                newList.add(pay)
            }
        }
        savePeriodData(idx, newList)
        toast("✅ تم حفظ التعديلات")
    }

    private fun randomSuffix(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
