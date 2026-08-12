package com.shawafi.smsapp.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shawafi.smsapp.AppViewModel
import com.shawafi.smsapp.SmsImport
import com.shawafi.smsapp.SmsPhone
import com.shawafi.smsapp.SmsRow
import com.shawafi.smsapp.SmsSender
import com.shawafi.smsapp.SmsStatus
import com.shawafi.smsapp.buildSmsMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Sky = Color(0xFF89CFF0)

private enum class Tab(val label: String) {
    SEND("إرسال"), HISTORY("السجل"), SETTINGS("الإعدادات")
}

@Composable
fun SmsMainScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val msg by vm.message.collectAsState()

    LaunchedEffect(msg) {
        msg?.let {
            scope.launch { snackbar.showSnackbar(it.text) }
            vm.clearToast()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    var tab by remember { mutableStateOf(Tab.SEND) }

    val rows by vm.smsRows.collectAsState()
    val sending by vm.smsSending.collectAsState()
    val paused by vm.smsPaused.collectAsState()

    fun sendAll() {
        if (!SmsSender.canSend(context)) {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        } else {
            vm.sendAllSms(context)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("تطبيق الرسائل", fontWeight = FontWeight.Bold) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.SEND -> Icons.Filled.Send
                                    Tab.HISTORY -> Icons.Filled.History
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = t.label
                            )
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.SEND -> SendTab(vm, rows, sending, paused, onSendAll = ::sendAll, canSend = SmsSender.canSend(context))
                Tab.HISTORY -> HistoryTab(vm)
                Tab.SETTINGS -> SettingsTab(vm)
            }
        }
    }
}

// ===================== تبويب الإرسال =====================

@Composable
private fun SendTab(
    vm: AppViewModel,
    rows: List<SmsRow>,
    sending: Boolean,
    paused: Boolean,
    onSendAll: () -> Unit,
    canSend: Boolean
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val res = SmsImport.parse(context, uri)
        if (res.error != null) {
            vm.toast("❌ فشل الاستيراد: " + res.error, true)
        } else {
            vm.importSmsRows(res.rows)
            val msg = "✅ تم استيراد ${res.rows.size} مشترك" +
                    if (res.invalidPhones.isNotEmpty()) "، وتم تجاهل ${res.invalidPhones.size} رقم غير صالح" else ""
            vm.toast(msg)
        }
    }
    var addDialog by remember { mutableStateOf(false) }
    val sentCount = rows.count { it.status == SmsStatus.SENT }

    if (addDialog) {
        AddRowDialog(
            onAdd = { name, phone ->
                addDialog = false
                val norm = SmsPhone.normalize(phone)
                if (SmsPhone.isValid(norm)) {
                    vm.importSmsRows(rows + SmsRow(
                        id = java.util.UUID.randomUUID().toString(),
                        phone = norm, name = name, status = SmsStatus.PENDING
                    ))
                } else {
                    vm.toast("رقم الجوال غير صالح: $phone", true)
                }
            },
            onDismiss = { addDialog = false }
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSendAll, enabled = !sending && rows.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(6.dp))
                if (sending) Text(if (paused) "⏸ متوقف مؤقتاً" else "إيقاف") else Text("إرسال الكل")
            }
            if (sending) {
                OutlinedButton(onClick = { vm.toggleSmsPause() }, modifier = Modifier.weight(1f)) {
                    Text(if (paused) "متابعة" else "إيقاف مؤقت")
                }
            } else {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf(
                    "text/*", "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.oasis.opendocument.spreadsheet"
                )) }, modifier = Modifier.weight(1f)) {
                    Text("استيراد كشف")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { addDialog = true }, modifier = Modifier.weight(1f)) {
                Text("+ إضافة يدوي")
            }
            OutlinedButton(onClick = { vm.clearSmsRows() }, enabled = rows.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("مسح الكل")
            }
        }
        if (!canSend) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "⚠️ الإذن غير مفعّل — اسمح بإرسال الرسائل من إعدادات النظام أو عاود الضغط على «إرسال الكل»",
                    modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                "الإجمالي: ${rows.size} • ناجح: $sentCount • متبقي: ${rows.size - sentCount}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                color = if (rows.size - sentCount > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
            )
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا يوجد مشتركون — استورد كشف Excel أو أضف يدوياً",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { row ->
                    SmsRowCard(
                        row = row,
                        sending = sending,
                        onResend = { vm.sendOneSms(context, row.id) },
                        onDelete = { vm.importSmsRows(rows.filterNot { it.id == row.id }) },
                        onPhoneChange = { vm.updateSmsRowPhone(row.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsRowCard(
    row: SmsRow,
    sending: Boolean,
    onResend: () -> Unit,
    onDelete: () -> Unit,
    onPhoneChange: (String) -> Unit
) {
    val statusColor = when (row.status) {
        SmsStatus.SENT -> Color(0xFF2E7D32)
        SmsStatus.FAILED -> Color(0xFFC62828)
        SmsStatus.SENDING -> Sky
        SmsStatus.PENDING -> Color(0xFF757575)
    }
    var editing by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.name.ifBlank { row.phone }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(row.phone, style = MaterialTheme.typography.bodySmall, color = Color(0xFF616161))
                    row.prevReading?.let { prev ->
                        row.curReading?.let { cur ->
                            val cons = (cur - prev).coerceAtLeast(0.0)
                            Text(
                                "👁 القراءة: $prev ← $cur ($cons)",
                                style = MaterialTheme.typography.bodySmall, color = Color(0xFF37474F)
                            )
                        }
                    }
                }
                Box(
                    Modifier.background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(statusLabel(row.status), color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            if (editing) {
                OutlinedTextField(
                    value = row.phone,
                    onValueChange = onPhoneChange,
                    label = { Text("رقم الجوال") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = { editing = false }, modifier = Modifier.weight(1f)) { Text("حفظ") }
                    OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                }
            } else {
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("تعديل رقم") }
                    OutlinedButton(onClick = onResend, enabled = row.status != SmsStatus.SENDING && !sending, modifier = Modifier.weight(1f)) {
                        Text(if (row.status == SmsStatus.SENT) "إعادة إرسال" else "إرسال")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFC62828))
                    }
                }
            }
        }
    }
}

private fun statusLabel(s: SmsStatus) = when (s) {
    SmsStatus.PENDING -> "بانتظار"
    SmsStatus.SENDING -> "جارٍ الإرسال"
    SmsStatus.SENT -> "تم الإرسال"
    SmsStatus.FAILED -> "فشل"
}

// ===================== تبويب السجل =====================

@Composable
private fun HistoryTab(vm: AppViewModel) {
    val history by vm.smsHistory.collectAsState()
    val f = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا يوجد سجل بعد", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("آخر 200 رسالة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearSmsHistory() }) { Text("مسح السجل", color = Color(0xFFC62828)) }
            }
        }
        items(history) { h ->
            Card(colors = CardDefaults.cardColors(containerColor = if (h.success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.name.ifBlank { h.phone }, fontWeight = FontWeight.Bold)
                        Text(h.phone + " • " + f.format(Date(h.time)), style = MaterialTheme.typography.bodySmall, color = Color(0xFF616161))
                    }
                    Text(if (h.success) "✅" else "❌", fontSize = 20.sp)
                }
            }
        }
    }
}

// ===================== تبويب الإعدادات =====================

@Composable
private fun SettingsTab(vm: AppViewModel) {
    val s by vm.smsSettings.collectAsState()
    val sample = remember(s) { SmsRow("x", "77xxxxxxx", "مشترك تجريبي", 100.0, 150.0, 0.0) }
    val rendered = buildSmsMessage(sample, s)

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text("حساب الفاتورة", fontWeight = FontWeight.Bold)
                    Text("القيمة = (حالية − سابقة) × سعر الكيلو + اشتراك شهري", style = MaterialTheme.typography.bodySmall, color = Color(0xFF616161))
                    Spacer(Modifier.height(8.dp))
                    _NumField("سعر الكيلووات (ريال)", s.priceKwh.toString(), { it.toDoubleOrNull()?.let(vm::setSmsPrice) })
                    Spacer(Modifier.height(8.dp))
                    _NumField("الاشتراك الشهري (ريال)", s.monthlyFee.toString(), { it.toDoubleOrNull()?.let(vm::setSmsMonthlyFee) })
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نص الرسالة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.resetSmsTemplate() }) { Text("استعادة الافتراضي") }
                    }
                    OutlinedTextField(
                        value = s.template, onValueChange = vm::setSmsTemplate,
                        modifier = Modifier.fillMaxWidth(), minLines = 4
                    )
                    Text(
                        "🅿️ المتغيرات: {name} الاسم • {phone} الجوال • {prev} القراءة السابقة • {cur} الحالية • {cons} الاستهلاك • {kwh} السعر • {fee} الاشتراك • {amount} القيمة • {ar} المتأخرات • {total} الإجمالي",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF37474F)
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("معاينة (مثال):", style = MaterialTheme.typography.bodySmall, color = Color(0xFF616161))
                    Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(rendered, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "طول الرسالة: ${rendered.length} حرف" + if (rendered.length > 160) " (ستُقسّم لرسائل متعددة)" else "",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF37474F), modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun _NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

// ===================== شاشة إضافة يدوي =====================

@Composable
private fun AddRowDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مشترك") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المشترك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الجوال") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name.trim(), phone.trim()) }) { Text("إضافة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}