package com.shawafi.tasdeed.data

import org.json.JSONObject

data class Branch(
    val key: String,
    val name: String,
    val username: String,
    val password: String,
    val syncKey: String = FirebaseClient.SYNC_KEY
) {
    companion object {
        fun from(key: String, o: JSONObject): Branch? {
            return try {
                Branch(
                    key = key,
                    name = o.optString("name"),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    syncKey = o.optString("sync_key", FirebaseClient.SYNC_KEY)
                )
            } catch (e: Exception) { null }
        }
    }
}

data class Subscriber(
    val key: String,
    val id: String,
    val name: String,
    val meterNumber: String,
    val subscriberNumber: String,
    val unpaidBalance: Double,
    val lastAmount: Double,
    val isActive: Boolean,
    val syncKey: String
) {
    val displayBalance: Double get() = unpaidBalance + lastAmount

    companion object {
        fun from(key: String, o: JSONObject): Subscriber? {
            return try {
                Subscriber(
                    key = key,
                    id = o.optString("id"),
                    name = o.optString("name"),
                    meterNumber = o.optString("meter_number"),
                    subscriberNumber = o.optString("subscriber_number"),
                    unpaidBalance = o.optDouble("unpaid_balance", 0.0),
                    lastAmount = o.optDouble("last_amount", 0.0),
                    isActive = o.optString("is_active") != "0",
                    syncKey = o.optString("sync_key")
                )
            } catch (e: Exception) { null }
        }
    }
}

data class PaymentRecord(
    val subscriberId: String = "",
    val subscriberName: String = "",
    val meterNumber: String = "",
    val subscriberNumber: String = "",
    val amount: Double = 0.0,
    val unpaidBalance: Double = 0.0,
    val note: String = "",
    val paymentMethod: String = "نقدي",
    val paymentDate: String = "",
    val branch: String = "",
    val branchName: String = "",
    val collectorUser: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val localId: String = "",
    val syncKey: String = FirebaseClient.SYNC_KEY,
    val periodIdx: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("subscriber_id", subscriberId)
        put("subscriber_name", subscriberName)
        put("meter_number", meterNumber)
        put("subscriber_number", subscriberNumber)
        put("amount", amount)
        put("unpaid_balance", unpaidBalance)
        put("note", note)
        put("payment_method", paymentMethod)
        put("payment_date", paymentDate)
        put("branch", branch)
        put("branch_name", branchName)
        put("collector_user", collectorUser)
        put("created_at", createdAt)
        put("local_id", localId)
        put("sync_key", syncKey)
        periodIdx?.let { put("_period_idx", it) }
    }

    companion object {
        fun from(o: JSONObject): PaymentRecord = PaymentRecord(
            subscriberId = o.optString("subscriber_id"),
            subscriberName = o.optString("subscriber_name"),
            meterNumber = o.optString("meter_number"),
            subscriberNumber = o.optString("subscriber_number"),
            amount = o.optDouble("amount", 0.0),
            unpaidBalance = o.optDouble("unpaid_balance", 0.0),
            note = o.optString("note"),
            paymentMethod = o.optString("payment_method", "نقدي"),
            paymentDate = o.optString("payment_date"),
            branch = o.optString("branch"),
            branchName = o.optString("branch_name"),
            collectorUser = o.optString("collector_user"),
            createdAt = o.optLong("created_at", System.currentTimeMillis()),
            localId = o.optString("local_id"),
            syncKey = o.optString("sync_key", FirebaseClient.SYNC_KEY),
            periodIdx = if (o.has("_period_idx")) o.optInt("_period_idx") else null
        )
    }
}

data class FreePayment(
    val amount: Double = 0.0,
    val beneficiary: String = "",
    val note: String = "",
    val paymentDate: String = "",
    val collectorUser: String = "",
    val branch: String = "",
    val branchName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val localId: String = "",
    val syncKey: String = FirebaseClient.SYNC_KEY
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("amount", amount)
        put("beneficiary", beneficiary)
        put("note", note)
        put("payment_date", paymentDate)
        put("collector_user", collectorUser)
        put("branch", branch)
        put("branch_name", branchName)
        put("created_at", createdAt)
        put("local_id", localId)
        put("sync_key", syncKey)
    }

    companion object {
        fun from(o: JSONObject): FreePayment = FreePayment(
            amount = o.optDouble("amount", 0.0),
            beneficiary = o.optString("beneficiary"),
            note = o.optString("note"),
            paymentDate = o.optString("payment_date"),
            collectorUser = o.optString("collector_user"),
            branch = o.optString("branch"),
            branchName = o.optString("branch_name"),
            createdAt = o.optLong("created_at", System.currentTimeMillis()),
            localId = o.optString("local_id"),
            syncKey = o.optString("sync_key", FirebaseClient.SYNC_KEY)
        )
    }
}

data class Period(
    val name: String,
    val payments: MutableList<PaymentRecord> = mutableListOf(),
    val createdAt: String = "",
    val closedAt: Long = System.currentTimeMillis()
)

data class AppConfig(
    val showCountLogin: Boolean = true
) {
    companion object {
        fun from(o: JSONObject?): AppConfig = AppConfig(
            showCountLogin = o?.optString("show_count_login") != "0"
        )
    }
}

data class Lock(val key: String, val expiresAt: Long)

// ---------- ميزة فواتير SMS ----------

const val DEFAULT_SMS_TEMPLATE = "م [رقم] مستحقات يوليو 26 قراءة سابقة [ق.سابقة] قراءة حالية [ق.حالية] استهلاك [استهلاك] قيمة الاستهلاك [قيمة] متأخرات [متأخرات] الإجمالي [إجمالي] كهرباء الشوافي"

object SmsPhone {
    fun normalize(raw: String): String {
        var p = raw.trim().replace(" ", "").replace("-", "").replace("+", "")
        if (p.startsWith("967")) p = p.drop(3)
        if (p.startsWith("0")) p = p.drop(1)
        return p
    }

    fun isValid(p: String): Boolean =
        p.length == 9 && p.startsWith("7") && p.all { it.isDigit() }
}

enum class SmsStatus { PENDING, SENDING, SENT, FAILED }

data class SmsSettings(
    val priceKwh: Double = 100.0,
    val monthlyFee: Double = 0.0,
    val template: String = DEFAULT_SMS_TEMPLATE
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("price_kwh", priceKwh)
        put("monthly_fee", monthlyFee)
        put("template", template)
    }

    companion object {
        fun from(o: JSONObject?): SmsSettings = SmsSettings(
            priceKwh = o?.optDouble("price_kwh", 100.0) ?: 100.0,
            monthlyFee = o?.optDouble("monthly_fee", 0.0) ?: 0.0,
            template = o?.optString("template", DEFAULT_SMS_TEMPLATE) ?: DEFAULT_SMS_TEMPLATE
        )
    }
}

fun fmtSmsNum(v: Double): String {
    val rounded = kotlin.math.round(v * 10) / 10
    return if (rounded == kotlin.math.floor(rounded) && !rounded.isInfinite()) {
        rounded.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", rounded)
    }
}

fun buildSmsMessage(row: SmsRow, s: SmsSettings): String {
    val value = row.valueAmount(s)
    val total = row.totalAmount(s)
    return s.template
        .replace("[رقم]", row.phone)
        .replace("[اسم]", row.name)
        .replace("[ق.سابقة]", fmtSmsNum(row.prevReading))
        .replace("[ق.حالية]", fmtSmsNum(row.curReading))
        .replace("[استهلاك]", fmtSmsNum(row.consumption))
        .replace("[قيمة]", fmtSmsNum(value))
        .replace("[متأخرات]", fmtSmsNum(row.arrears))
        .replace("[إجمالي]", fmtSmsNum(total))
}

data class SmsRow(
    val id: String,
    val phone: String,
    val name: String,
    val prevReading: Double,
    val curReading: Double,
    val arrears: Double,
    val status: SmsStatus = SmsStatus.PENDING
) {
    val consumption: Double get() = curReading - prevReading

    fun valueAmount(s: SmsSettings): Double = consumption * s.priceKwh + s.monthlyFee

    fun totalAmount(s: SmsSettings): Double = valueAmount(s) + arrears

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("phone", phone)
        put("name", name)
        put("prev", prevReading)
        put("cur", curReading)
        put("arrears", arrears)
        put("status", status.name)
    }

    companion object {
        fun from(o: JSONObject): SmsRow = SmsRow(
            id = o.optString("id"),
            phone = o.optString("phone"),
            name = o.optString("name"),
            prevReading = o.optDouble("prev", 0.0),
            curReading = o.optDouble("cur", 0.0),
            arrears = o.optDouble("arrears", 0.0),
            status = runCatching { SmsStatus.valueOf(o.optString("status", "PENDING")) }.getOrDefault(SmsStatus.PENDING)
        )
    }
}

data class SmsHistoryEntry(
    val id: String,
    val phone: String,
    val name: String,
    val message: String,
    val ts: Long,
    val success: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("phone", phone)
        put("name", name)
        put("message", message)
        put("ts", ts)
        put("success", success)
    }

    companion object {
        fun from(o: JSONObject): SmsHistoryEntry = SmsHistoryEntry(
            id = o.optString("id"),
            phone = o.optString("phone"),
            name = o.optString("name"),
            message = o.optString("message"),
            ts = o.optLong("ts", System.currentTimeMillis()),
            success = o.optBoolean("success", true)
        )
    }
}
