package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    onNav: (String) -> Unit,
    onSettings: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val subscribers by vm.subscribers.collectAsState()
    val locks by vm.locks.collectAsState()
    val now by vm.nowTick.collectAsState()
    val devMode by vm.devMode.collectAsState()
    var selected by remember { mutableStateOf<Subscriber?>(null) }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الفواتير", onRefresh = { vm.reloadSubscribers() }, onSettings = onSettings)

        // search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("ابحث باسم المشترك / رقم العداد...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val filtered = remember(query, subscribers) {
            subscribers.filter {
                query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.meterNumber.contains(query, true) ||
                it.subscriberNumber.contains(query, true)
            }
        }

        if (query.isBlank()) {
            if (devMode) {
                val dueSum = remember(subscribers) { subscribers.filter { it.displayBalance > 0 }.sumOf { it.displayBalance } }
                val dueCount = remember(subscribers) { subscribers.count { it.displayBalance > 0 } }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Green),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📊 ملخص الحسابات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            SummaryCell("المشتركين", subscribers.size.toString(), Modifier.weight(1f))
                            SummaryCell("مطلوب منهم", dueCount.toString(), Modifier.weight(1f))
                            SummaryCell("إجمالي المطلوب", "${formatNum(dueSum)} د.ع", Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("🔍 ابحث باسم المشترك...", color = Color.Gray)
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("لا يوجد مشتركين", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.key }) { sub ->
                    SubscriberCard(sub, isLocked = (locks[sub.key] ?: 0) > now) {
                        selected = sub
                    }
                }
            }
        }
    }

    selected?.let { sub ->
        PayDialog(vm, sub, (locks[sub.key] ?: 0L) > now) { selected = null }
    }
}

@Composable
fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
fun SubscriberCard(sub: Subscriber, isLocked: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLocked) Brush.linearGradient(listOf(Amber, Amber))
                        else Brush.linearGradient(listOf(GreenLight, Green))
                    ),
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

private val sharedNumFmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)

fun formatNum(v: Double): String {
    return try {
        sharedNumFmt.format(v)
    } catch (e: Exception) { java.lang.String.format("%.0f", v) }
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
