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
    val syncKey: String,
    val hidden: Boolean = false,
    val hideAmounts: Boolean = false
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
                    syncKey = o.optString("sync_key"),
                    hidden = o.optInt("hidden", 0) == 1,
                    hideAmounts = o.optInt("hide_amounts", 0) == 1
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

