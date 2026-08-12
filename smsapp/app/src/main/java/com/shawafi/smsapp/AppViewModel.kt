package com.shawafi.smsapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class ToastMsg(val text: String, val isError: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LocalStore(app)

    val message = MutableStateFlow<ToastMsg?>(null)

    val smsRows = MutableStateFlow<MutableList<SmsRow>>(store.loadSmsRows())
    val smsHistory = MutableStateFlow<MutableList<SmsHistoryEntry>>(store.loadSmsHistory())
    val smsSettings = MutableStateFlow(store.loadSmsSettings())
    val smsSending = MutableStateFlow(false)
    val smsPaused = MutableStateFlow(false)

    private var smsJob: Job? = null

    fun toast(text: String, isError: Boolean = false) {
        message.value = ToastMsg(text, isError)
    }

    fun clearToast() {
        message.value = null
    }

    fun importSmsRows(rows: List<SmsRow>) {
        smsRows.value = rows.toMutableList()
        store.saveSmsRows(smsRows.value)
    }

    fun clearSmsRows() {
        smsJob?.cancel()
        smsJob = null
        smsSending.value = false
        smsPaused.value = false
        smsRows.value = mutableListOf()
        store.saveSmsRows(smsRows.value)
    }

    fun clearSmsHistory() {
        smsHistory.value = mutableListOf()
        store.saveSmsHistory(smsHistory.value)
    }

    fun updateSmsRowPhone(id: String, phone: String) {
        val l = smsRows.value.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        if (i >= 0) {
            l[i] = l[i].copy(phone = SmsPhone.normalize(phone))
            smsRows.value = l
            store.saveSmsRows(l)
        }
    }

    fun setSmsPrice(v: Double) {
        val s = smsSettings.value.copy(priceKwh = v)
        smsSettings.value = s
        store.saveSmsSettings(s)
    }

    fun setSmsMonthlyFee(v: Double) {
        val s = smsSettings.value.copy(monthlyFee = v)
        smsSettings.value = s
        store.saveSmsSettings(s)
    }

    fun setSmsTemplate(t: String) {
        val s = smsSettings.value.copy(template = t)
        smsSettings.value = s
        store.saveSmsSettings(s)
    }

    fun resetSmsTemplate() {
        setSmsTemplate(DEFAULT_SMS_TEMPLATE)
    }

    fun toggleSmsPause() {
        smsPaused.value = !smsPaused.value
    }

    fun smsResult(rowId: String, success: Boolean) {
        val l = smsRows.value.toMutableList()
        val i = l.indexOfFirst { it.id == rowId }
        var name = ""
        var phone = ""
        if (i >= 0) {
            l[i] = l[i].copy(status = if (success) SmsStatus.SENT else SmsStatus.FAILED)
            name = l[i].name
            phone = l[i].phone
            smsRows.value = l
            store.saveSmsRows(l)
        }
        val message = l.getOrNull(i)?.let { buildSmsMessage(it, smsSettings.value) } ?: ""
        val h = (listOf(SmsHistoryEntry(rowId, phone, name, message, System.currentTimeMillis(), success)) + smsHistory.value).take(200).toMutableList()
        smsHistory.value = h
        store.saveSmsHistory(h)
        if (!success) toast("⚠️ فشل إرسال: ${name.ifBlank { phone }}", true)
    }

    fun sendAllSms(ctx: Context) {
        if (smsSending.value || smsRows.value.isEmpty()) return
        smsJob?.cancel()
        smsJob = viewModelScope.launch {
            smsSending.value = true
            smsPaused.value = false
            val targets = smsRows.value.filter { it.status == SmsStatus.PENDING || it.status == SmsStatus.FAILED }
            var ok = 0
            var failed = 0
            targets.forEach { row ->
                while (smsPaused.value) delay(300)
                if (!smsSending.value) return@launch
                val l = smsRows.value.toMutableList()
                val i = l.indexOfFirst { it.id == row.id }
                if (i >= 0) {
                    l[i] = l[i].copy(status = SmsStatus.SENDING)
                    smsRows.value = l
                }
                val sent = SmsSender.send(ctx, row.id, row.phone, buildSmsMessage(row, smsSettings.value))
                if (!sent) {
                    failed++
                    smsResult(row.id, false)
                } else {
                    ok++
                }
                delay(1800)
            }
            smsSending.value = false
            smsPaused.value = false
            if (ok > 0) toast("✅ تم إرسال $ok رسالة" + if (failed > 0) "، فشلت $failed" else "")
            else if (failed > 0) toast("❌ فشل الإرسال (تحقق من إذن SMS والرصيد)", true)
        }
    }

    fun sendOneSms(ctx: Context, rowId: String) {
        val row = smsRows.value.firstOrNull { it.id == rowId } ?: return
        val l = smsRows.value.toMutableList()
        val i = l.indexOfFirst { it.id == rowId }
        if (i >= 0) {
            l[i] = l[i].copy(status = SmsStatus.SENDING)
            smsRows.value = l
        }
        viewModelScope.launch {
            val sent = SmsSender.send(ctx, row.id, row.phone, buildSmsMessage(row, smsSettings.value))
            if (!sent) smsResult(rowId, false)
        }
    }

    fun stopSmsSending() {
        smsJob?.cancel()
        smsJob = null
        smsSending.value = false
        smsPaused.value = false
    }

    override fun onCleared() {
        super.onCleared()
        smsJob?.cancel()
    }
}