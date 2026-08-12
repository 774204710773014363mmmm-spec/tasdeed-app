package com.shawafi.tasdeed.ui.screens

import android.Manifest
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.data.SmsImport
import com.shawafi.tasdeed.data.SmsPhone
import com.shawafi.tasdeed.data.SmsRow
import com.shawafi.tasdeed.data.SmsSender
import com.shawafi.tasdeed.data.SmsStatus
import com.shawafi.tasdeed.data.buildSmsMessage
import com.shawafi.tasdeed.data.fmtSmsNum
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PLACEHOLDERS = listOf("[رقم]", "[اسم]", "[ق.سابقة]", "[ق.حالية]", "[استهلاك]", "[قيمة]", "[متأخرات]", "[إجمالي]")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onBack: () -> Unit
) {
    val rows by vm.smsRows.collectAsState()
    val history by vm.smsHistory.collectAsState()
    val settings by vm.smsSettings.collectAsState()
    val sending by vm.smsSending.collectAsState()
    val paused by vm.smsPaused.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var invalidCount by remember { mutableStateOf(0) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val res = withContext(Dispatchers.IO) { SmsImport.parse(ctx, uri) }
            importing = false
            if (res.rows.isEmpty() && res.invalidPhones.isEmpty()) {
                vm.toast("❌ لم نجد بيانات في الملف", true)
            } else {
                vm.importSmsRows(res.rows)
                invalidCount = res.invalidPhones.size
                vm.toast("✅ تم استيراد ${res.rows.size} مشترك" + if (res.invalidPhones.isNotEmpty()) "، منها ${res.invalidPhones.size} رقم غير صالح" else "")
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.toast("✅ تم منح إذن الإرسال")
            vm.sendAllSms(ctx)
        } else {
            vm.toast("❌ بدون إذن SMS لا يمكن الإرسال", true)
        }
    }

    val visibleRows = rows.filter { it.status != SmsStatus.SENT }
    val pendingCount = rows.count { it.status == SmsStatus.PENDING || it.status == SmsStatus.FAILED }

    Column(modifier = modifier.padding(padding).fillMaxSize()) {
        TopAppBar(
            modifier = Modifier.background(GreenBrush),
            title = {
                Column {
                    Text("📨 فواتير SMS", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("إرسال جماعي بكشف من Excel/CSV", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f))
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, actionIconContentColor = Color.White)
        )

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("📨 الإرسال") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🗂 السجل") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("⚙️ الإعدادات") })
        }

        when (tab) {
            0 -> Column(Modifier.fillMaxSize()) {
                // أزرار التحكم
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { openFile.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        enabled = !importing && !sending
                    ) { Text(if (importing) "🔄 جارٍ الاستيراد..." else "📂 استيراد Excel/CSV") }
                    OutlinedButton(
                        onClick = { vm.clearSmsRows() },
                        modifier = Modifier.weight(1f).height(46.dp),
                        enabled = rows.isNotEmpty() && !sending
                    ) { Text("🗑 مسح القائمة") }
                }

                if (invalidCount > 0) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️ يوجد $invalidCount رقم غير صالح - عدّلها من البطاقة أدناه قبل الإرسال", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                    }
                }

                if (rows.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (!SmsSender.canSend(ctx)) permLauncher.launch(Manifest.permission.SEND_SMS)
                                else vm.sendAllSms(ctx)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            enabled = pendingCount > 0 && !sending,
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("📨 إرسال الكل ($pendingCount)", fontWeight = FontWeight.Bold) }
                        if (sending) {
                            OutlinedButton(onClick = { vm.toggleSmsPause() }, modifier = Modifier.weight(1f).height(46.dp)) {
                                Text(if (paused) "▶️ استئناف" else "⏸ إيقاف مؤقت")
                            }
                        }
                    }
                    if (sending) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(if (paused) "🛑 الإرسال متوقف مؤقتاً" else "🔄 جارٍ الإرسال... (${rows.count { it.status == SmsStatus.SENT }} من ${rows.size})", fontSize = 12.sp, color = if (paused) Color(0xFFDC2626) else Color.Gray)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.stopSmsSending() }) { Text("🛑 إيقاف", color = Color(0xFFDC2626)) }
                        }
                    }
                }

                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📭", fontSize = 42.sp)
                            Text("لم يتم استيراد كشف بعد", color = Color.Gray, fontSize = 14.sp)
                            Text("اضغط «استيراد Excel/CSV» للبدء", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleRows, key = { it.id }) { row ->
                            SmsRowCard(vm, row, settings.priceKwh, settings.monthlyFee, buildSmsMessage(row, settings), sending, ctx)
                        }
                    }
                }
            }

            1 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🗂 السجل (${history.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearSmsHistory() }, enabled = history.isNotEmpty()) { Text("🗑 مسح السجل", color = Color(0xFFDC2626)) }
                }
                if (history.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🗂", fontSize = 40.sp)
                            Text("لا توجد رسائل مرسلة بعد", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.id + it.ts }) { h ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (h.success) "✅" else "❌", fontSize = 14.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(h.name.ifBlank { "بدون اسم" }, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(h.ts)),
                                            fontSize = 10.5.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text("📱 ${h.phone}", fontSize = 12.sp, color = if (h.success) Green else Color(0xFFDC2626))
                                    Text(h.message, fontSize = 11.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("🧮 الحسابات التلقائية", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("الاستهلاك = الحالية - السابقة", fontSize = 12.sp, color = Color.Gray)
                            Text("قيمة الاستهلاك = (الاستهلاك × سعر الكيلو) + الاشتراك الثابت", fontSize = 12.sp, color = Color.Gray)
                            Text("الإجمالي = قيمة الاستهلاك + المتأخرات", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(12.dp))
                            SmsNumField("سعر الكيلو (د.ع)", settings.priceKwh) { vm.setSmsPrice(it) }
                            Spacer(Modifier.height(8.dp))
                            SmsNumField("الاشتراك الشهري الثابت (د.ع)", settings.monthlyFee) { vm.setSmsMonthlyFee(it) }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📝 صيغة الرسالة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.resetSmsTemplate() }) { Text("↩️ استعادة الافتراضي", fontSize = 12.sp) }
                            }
                            OutlinedTextField(
                                value = settings.template,
                                onValueChange = { vm.setSmsTemplate(it) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                            Spacer(Modifier.height(8.dp))
                            val sampleMsg = buildSmsMessage(
                                SmsRow("sample", "777777777", "مثال", 12500.0, 13500.0, 2000.0),
                                settings
                            )
                            val parts = remember(sampleMsg) { runCatching { SmsManager.getDefault().divideMessage(sampleMsg).size }.getOrDefault(1) }
                            Text(
                                if (parts > 1)
                                    "🔔 ${sampleMsg.length} حرف - ستُرسل في $parts رسالة (تكلفة أعلى)"
                                else
                                    "✓ ${sampleMsg.length} حرف - رسالة واحدة",
                                fontSize = 12.sp,
                                color = if (parts > 1) Color(0xFFDC2626) else Green
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("الحقول المتاحة (اضغط للإضافة):", fontSize = 12.sp, color = Color.Gray)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(PLACEHOLDERS) { ph ->
                                    AssistChip(
                                        onClick = { vm.setSmsTemplate(settings.template + " " + ph) },
                                        label = { Text(ph, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("💡 ملاحظات", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("• الرسائل قد تكون برسوم إذا لم تكن ضمن باقة - تأكد من رصيد الشريحة.", fontSize = 12.sp, color = Color.Gray)
                            Text("• يوجد فاصل زمني بين الرسائل لتجنب حظر الشريحة.", fontSize = 12.sp, color = Color.Gray)
                            Text("• الرسائل تُرسل قيمتها بالأرقام اللاتينية داخل نص عربي (RTL).", fontSize = 12.sp, color = Color.Gray)
                            Text("• المشترك يُخفى من القائمة فور نجاح إرساله، والفشل يبقى مع زر «إعادة محاولة».", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmsRowCard(
    vm: AppViewModel,
    row: SmsRow,
    price: Double,
    monthlyFee: Double,
    message: String,
    sending: Boolean,
    ctx: android.content.Context
) {
    val valid = SmsPhone.isValid(row.phone)
    val buying = row.status == SmsStatus.SENDING && sending
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name.ifBlank { "بدون اسم" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                when (row.status) {
                    SmsStatus.SENDING -> if (buying) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    SmsStatus.FAILED -> Text("⚠️ فشل", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    else -> {}
                }
            }
            Spacer(Modifier.height(4.dp))
            if (row.status == SmsStatus.FAILED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ فشل الإرسال (شبكة/رصيد/إذن)", fontSize = 11.sp, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.sendOneSms(ctx, row.id) }, enabled = !sending) {
                        Text("🔄 إعادة محاولة", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            OutlinedTextField(
                value = row.phone,
                onValueChange = { vm.updateSmsRowPhone(row.id, it) },
                label = { Text("📱 رقم الجوال") },
                singleLine = true,
                isError = !valid,
                supportingText = if (!valid) { { Text("رقم غير صالح", fontSize = 10.sp) } } else null,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text("ق.سابقة ${fmtSmsNum(row.prevReading)} | ق.حالية ${fmtSmsNum(row.curReading)} | استهلاك ${fmtSmsNum(row.consumption)}", fontSize = 11.5.sp, color = Color.Gray)
            Text(
                "القيمة ${fmtSmsNum(row.consumption * price + monthlyFee)} | متأخرات ${fmtSmsNum(row.arrears)} | الإجمالي ${fmtSmsNum(row.consumption * price + monthlyFee + row.arrears)}",
                fontSize = 11.5.sp, color = Green, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(message, fontSize = 11.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SmsNumField(label: String, value: Double, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(fmtSmsNum(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onCommit(it.toDoubleOrNull() ?: 0.0) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}