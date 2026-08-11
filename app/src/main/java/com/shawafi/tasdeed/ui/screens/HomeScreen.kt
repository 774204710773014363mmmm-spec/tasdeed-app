package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shawafi.tasdeed.data.Subscriber
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenLight
import com.shawafi.tasdeed.ui.theme.Amber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val subscribers by vm.subscribers.collectAsState()
    val locks by vm.locks.collectAsState()
    var selected by remember { mutableStateOf<Subscriber?>(null) }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الفواتير", onRefresh = { vm.reloadSubscribers() })

        // search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("🔍 ابحث باسم المشترك / رقم العداد...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val filtered = subscribers.filter {
            query.isBlank() ||
            it.name.contains(query, ignoreCase = true) ||
            it.meterNumber.contains(query, true) ||
            it.subscriberNumber.contains(query, true)
        }

        if (query.isBlank()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("🔍 ابحث باسم المشترك...", color = Color.Gray)
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("لا يوجد مشتركين", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { sub ->
                    SubscriberCard(sub, isLocked = (locks[sub.key] ?: 0) > System.currentTimeMillis()) {
                        selected = sub
                    }
                }
            }
        }
    }

    selected?.let { sub ->
        PayDialog(vm, sub, (locks[sub.key] ?: 0L) > System.currentTimeMillis()) { selected = null }
    }
}

@Composable
fun SubscriberCard(sub: Subscriber, isLocked: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (isLocked) Amber else Green),
                contentAlignment = Alignment.Center
            ) {
                Text(sub.name.take(2).ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sub.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("📟 ${sub.meterNumber.ifEmpty { "-" }}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "💰 ${formatNum(sub.displayBalance)} د.ع",
                        fontSize = 12.sp,
                        color = if (sub.displayBalance > 0) Color(0xFFDC2626) else Green
                    )
                }
            }
            if (isLocked) {
                Text("🔒", fontSize = 18.sp)
            }
        }
    }
}

fun formatNum(v: Double): String {
    val s = java.lang.String.format("%.0f", v)
    return try {
        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(v).replace(",", ",")
    } catch (e: Exception) { s }
}

@Composable
fun PayDialog(vm: AppViewModel, sub: Subscriber, locked: Boolean, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(if (sub.displayBalance > 0) sub.displayBalance.toString() else "") }
    var note by remember { mutableStateOf("") }
    var periodIdx by remember { mutableStateOf<Int?>(null) }
    val periods by vm.periods.collectAsState()
    val scope = rememberCoroutineScope()
    val isCurrent = periodIdx == null

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("💰 تسديد: ${sub.name}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text("📟 ${sub.meterNumber.ifEmpty { "-" }} | المطلوب: ${formatNum(sub.displayBalance)} د.ع", fontSize = 12.sp, color = Color.Gray)

                if (locked) {
                    Spacer(Modifier.height(12.dp))
                    Text("🔒 هذا المشترك مقفل حالياً من جهاز آخر", color = Amber, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ (د.ع)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                // period selector
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (isCurrent) "📋 الكشف الحالي" else "📁 ${periods.getOrNull(periodIdx!!)?.name ?: ""}", modifier = Modifier.weight(1f))
                    Text("▼", color = Color.Gray)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("📋 الكشف الحالي") }, onClick = { periodIdx = null; expanded = false })
                    periods.forEachIndexed { i, p ->
                        DropdownMenuItem(text = { Text("📁 ${p.name}") }, onClick = { periodIdx = i; expanded = false })
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) {
                                vm.toast("أدخل مبلغ صحيح", true)
                                return@Button
                            }
                            if (!locked) {
                                vm.acquireLock(sub.key)
                                vm.recordPayment(sub, amt, note.trim(), periodIdx)
                                scope.launch { kotlinx.coroutines.delay(5000); vm.releaseLock(sub.key) }
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Text("تأكيد الدفع")
                    }
                }
            }
        }
    }
}
