package com.shawafi.tasdeed.data

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

object SmsSender {
    const val ACTION_SENT = "com.shawafi.tasdeed.SMS_SENT"
    private val pendingParts = ConcurrentHashMap<String, Int>()

    fun register(ctx: Context, onResult: (rowId: String, success: Boolean) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val rowId = intent.getStringExtra("row_id") ?: return
                if (resultCode == Activity.RESULT_OK) {
                    val left = pendingParts.compute(rowId) { _, v -> (v ?: 1) - 1 } ?: 1
                    if (left <= 0) {
                        pendingParts.remove(rowId)
                        onResult(rowId, true)
                    }
                } else {
                    pendingParts.remove(rowId)
                    onResult(rowId, false)
                }
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(ACTION_SENT), ContextCompat.RECEIVER_EXPORTED)
        return receiver
    }

    fun unregister(ctx: Context, receiver: BroadcastReceiver) {
        try {
            ctx.unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
    }

    fun clearPending() {
        pendingParts.clear()
    }

    fun canSend(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * إرسال رسالة واحدة. يعيد true إذا تم تسليمها للنظام (النجاح النهائي يصل عبر الـ Broadcast).
     * يعيد false عند فشل فوري (رقم فارغ / إذن مفقود / استثناء).
     */
    fun send(ctx: Context, rowId: String, phone: String, message: String): Boolean {
        if (phone.isBlank() || message.isBlank()) return false
        if (!canSend(ctx)) return false
        return try {
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(message)
            pendingParts.remove(rowId)
            pendingParts[rowId] = parts.size
            val pIntents = parts.mapIndexed { i, _ ->
                PendingIntent.getBroadcast(
                    ctx,
                    rowId.hashCode() + i,
                    Intent(ACTION_SENT).putExtra("row_id", rowId).putExtra("part", i),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            if (parts.size == 1) {
                sms.sendTextMessage(phone, null, message, pIntents[0], null)
            } else {
                sms.sendMultipartTextMessage(phone, null, parts, java.util.ArrayList(pIntents), null)
            }
            true
        } catch (e: Exception) {
            pendingParts.remove(rowId)
            false
        }
    }
}