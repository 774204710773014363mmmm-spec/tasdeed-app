package com.shawafi.smsapp

import org.json.JSONObject

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
        .replace("[اشتراك]", row.subscriberNo)
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
    val subscriberNo: String = "",
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
        put("sub_no", subscriberNo)
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
            subscriberNo = o.optString("sub_no", ""),
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