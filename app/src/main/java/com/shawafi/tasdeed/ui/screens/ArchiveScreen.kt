package com.shawafi.tasdeed.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shawafi.tasdeed.data.PaymentRecord
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.Amber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit
) {
    var detail by remember { mutableStateOf<Pair<String, Int>?>(null) } // name to index (-1 = current)
    var showNew by remember { mutableStateOf(false) }
    val currentPayments by vm.currentPayments.collectAsState()
    val periods by vm.periods.collectAsState()
    val currentTotal = currentPayments.sumOf { it.amount }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الكشوفات", onRefresh = { vm.syncPendingPayments() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋 الكشف الحالي", fontWeight = FontWeight.Bold)
                        Text("${currentPayments.size} دفعة | ${formatNum(currentTotal)} د.ع", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        if (currentPayments.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { detail = "current" to -1 },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("📄 عرض الكشف") }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { showNew = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("➕ فتح كشف جديد", fontWeight = FontWeight.Bold) }
            }
            item { Text("📦 الكشوفات المغلقة", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(periods) { p ->
                Card(modifier = Modifier.fillMaxWidth().clickable { detail = p.name to periods.indexOf(p) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📁 ${p.name}", fontWeight = FontWeight.SemiBold)
                            Text("${p.payments.size} دفعة | ${formatNum(p.payments.sumOf { it.amount })} د.ع", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("🗓 ${p.createdAt}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            if (periods.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { Text("لا توجد كشوفات مغلقة", color = Color.Gray) } }
            }
        }
    }

    detail?.let { (name, idx) ->
        PeriodDetailDialog(vm, name, idx, onDismiss = { detail = null })
    }

    if (showNew) {
        NewPeriodDialog(vm, onDismiss = { showNew = false })
    }
}

@Composable
fun NewPeriodDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("➕ فتح كشف جديد", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("سيتم نقل دفعات الكشف الحالي إلى الكشف الجديد", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الكشف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            if (name.isBlank()) { vm.toast("❌ أدخل اسم الكشف", true); return@Button }
                            vm.newPeriod(name.trim())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) { Text("فتح") }
                }
            }
        }
    }
}

@Composable
fun PeriodDetailDialog(vm: AppViewModel, name: String, idx: Int, onDismiss: () -> Unit) {
    val isCurrent = idx < 0
    val currentPayments by vm.currentPayments.collectAsState()
    val periods by vm.periods.collectAsState()
    val list = if (isCurrent) currentPayments else periods.getOrNull(idx)?.payments ?: emptyList()
    val total = list.sumOf { it.amount }
    var editMode by remember { mutableStateOf(false) }
    var editedAmounts by remember { mutableStateOf<MutableMap<String, Double>>(mutableMapOf()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp).heightIn(max = 600.dp)) {
                Text(if (isCurrent) "📋 $name" else "📁 $name", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${list.size} دفعة | ${formatNum(total)} د.ع", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد دفعات", color = Color.Gray)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list.sortedByDescending { it.createdAt }) { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (editMode) {
                                    OutlinedTextField(
                                        value = editedAmounts[p.localId]?.let { formatNum(it) } ?: formatNum(p.amount),
                                        onValueChange = { editedAmounts[p.localId] = it.toDoubleOrNull() ?: 0.0 },
                                        singleLine = true,
                                        modifier = Modifier.width(110.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("د.ع", fontSize = 11.sp, color = Color.Gray)
                                } else {
                                    Text(p.subscriberName, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                }
                                if (!editMode) {
                                    Text("📅 ${p.paymentDate}", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(Modifier.width(10.dp))
                                    Text("${formatNum(p.amount)} د.ع", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Green)
                                }
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                        }
                    }
                    if (editMode) {
                        Spacer(Modifier.height(6.dp))
                        Text("💾 الإجمالي: ${formatNum(editedAmounts.values.sum())} د.ع", fontSize = 12.sp, color = Green)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp)) { Text("إغلاق") }
                    if (editMode) {
                        Button(
                            onClick = {
                                vm.saveEditedPeriod(isCurrent, idx, editedAmounts.keys.toSet(), editedAmounts)
                                editMode = false
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("💾 حفظ") }
                        OutlinedButton(onClick = { editMode = false }, modifier = Modifier.weight(1f).height(44.dp)) { Text("إلغاء") }
                    } else {
                        OutlinedButton(
                            onClick = { editMode = true },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("✏️ تعديل") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val ctx = vm.getApplication<android.app.Application>()
                            val f = ReportExporter.exportPdf(ctx, vm, name, list)
                            if (f != null) ReportExporter.share(ctx, f)
                            else vm.toast("لا يوجد مدفوعات", true)
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) { Text("📄 PDF") }
                    Button(
                        onClick = {
                            val ctx = vm.getApplication<android.app.Application>()
                            val f = ReportExporter.exportExcel(ctx, vm, name, list)
                            if (f != null) ReportExporter.share(ctx, f)
                            else vm.toast("لا يوجد مدفوعات", true)
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF16A34A))
                    ) { Text("📊 Excel") }
                    if (!isCurrent) {
                        OutlinedButton(
                            onClick = {
                                vm.deletePeriod(idx)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) { Text("🗑 حذف") }
                    }
                }
            }
        }
    }
}
