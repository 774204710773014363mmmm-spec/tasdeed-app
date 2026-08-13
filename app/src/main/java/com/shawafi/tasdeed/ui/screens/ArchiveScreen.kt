package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shawafi.tasdeed.data.Period
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit,
    onOpenStatement: (String, Int, String) -> Unit,
    onSettings: () -> Unit = {}
) {
    var showNew by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<Int?>(null) }
    var renameTarget by remember { mutableStateOf<Int?>(null) }
    var infoTarget by remember { mutableStateOf<Int?>(null) }
    var deleteTarget by remember { mutableStateOf<Int?>(null) }
    val currentPayments by vm.currentPayments.collectAsState()
    val periods by vm.periods.collectAsState()
    val currentTotal = currentPayments.sumOf { it.amount }
    val currentSubCount = remember(currentPayments) { ReportExporter.groupPayments(currentPayments).size }
    val periodSubCounts = remember(periods) { periods.map { ReportExporter.groupPayments(it.payments).size } }

    LaunchedEffect(Unit) { vm.fetchArchiveFromCloud() }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الكشوفات", onRefresh = { vm.syncPendingPayments(); vm.fetchArchiveFromCloud() }, onSettings = onSettings)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋 الكشف الحالي", fontWeight = FontWeight.Bold)
                        Text("${currentSubCount} مشترك | ${formatNum(currentTotal)} د.ع", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        if (currentPayments.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { onOpenStatement("الكشف الحالي", -1, "archive") },
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
            items(periods, key = { it.name + it.createdAt }) { p ->
                val pIdx = periods.indexOf(p)
                Card(modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onOpenStatement(p.name, pIdx, "archive") },
                    onLongClick = { menuTarget = pIdx }
                )) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📁 ${p.name}", fontWeight = FontWeight.SemiBold)
                            Text("${periodSubCounts.getOrElse(pIdx) { 0 }} مشترك | ${formatNum(p.payments.sumOf { it.amount })} د.ع", fontSize = 12.sp, color = Color.Gray)
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

    if (showNew) {
        NewPeriodDialog(
            title = "➕ فتح كشف جديد",
            subtitle = "سيتم نقل دفعات الكشف الحالي إلى الكشف الجديد",
            buttonText = "فتح",
            onOpen = { vm.newPeriod(it) },
            onDismiss = { showNew = false }
        )
    }

    // قائمة الضغطة المطولة على كشف مغلق
    val menuIdx = menuTarget
    if (menuIdx != null) {
        val p = periods.getOrNull(menuIdx)
        if (p != null) {
            ModalBottomSheet(onDismissRequest = { menuTarget = null }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text("📁 ${p.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    ListItem(
                        headlineContent = { Text("✏️ تعديل اسم الكشف") },
                        modifier = Modifier.clickable { menuTarget = null; renameTarget = menuIdx }
                    )
                    ListItem(
                        headlineContent = { Text("ℹ️ معلومات الكشف") },
                        modifier = Modifier.clickable { menuTarget = null; infoTarget = menuIdx }
                    )
                    ListItem(
                        headlineContent = { Text("🗑 حذف الكشف", color = Color(0xFFDC2626)) },
                        modifier = Modifier.clickable { menuTarget = null; deleteTarget = menuIdx }
                    )
                }
            }
        } else {
            menuTarget = null
        }
    }

    // تعديل اسم الكشف
    val renIdx = renameTarget
    if (renIdx != null) {
        val p = periods.getOrNull(renIdx)
        if (p != null) {
            RenamePeriodDialog(p.name, onSave = { vm.renamePeriod(renIdx, it) }) { renameTarget = null }
        } else {
            renameTarget = null
        }
    }

    // معلومات الكشف
    val infIdx = infoTarget
    if (infIdx != null) {
        val p = periods.getOrNull(infIdx)
        if (p != null) {
            PeriodInfoDialog(vm, p) { infoTarget = null }
        } else {
            infoTarget = null
        }
    }

    // تأكيد الحذف
    val delIdx = deleteTarget
    if (delIdx != null) {
        val p = periods.getOrNull(delIdx)
        if (p != null) {
            DeletePeriodDialog(p.name, onConfirm = { vm.deletePeriod(delIdx) }) { deleteTarget = null }
        } else {
            deleteTarget = null
        }
    }
}

@Composable
fun RenamePeriodDialog(currentName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("✏️ تعديل اسم الكشف", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                            if (name.isBlank()) return@Button
                            onSave(name.trim())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) { Text("حفظ") }
                }
            }
        }
    }
}

@Composable
fun PeriodInfoDialog(vm: AppViewModel, p: Period, onDismiss: () -> Unit) {
    val subCount = ReportExporter.groupPayments(p.payments).size
    val total = p.payments.sumOf { it.amount }
    val dates = p.payments.map { it.paymentDate }
    val first = dates.minOrNull() ?: "-"
    val last = dates.maxOrNull() ?: "-"
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("ℹ️ معلومات الكشف", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("📁 ${p.name}", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(14.dp))
                Text("🕐 فُتح بتاريخ: ${p.createdAt}", fontSize = 13.5.sp)
                Spacer(Modifier.height(6.dp))
                Text("📄 عدد الدفعات: ${p.payments.size}", fontSize = 13.5.sp)
                Spacer(Modifier.height(6.dp))
                Text("👥 عدد المشتركين: $subCount", fontSize = 13.5.sp)
                Spacer(Modifier.height(6.dp))
                Text("💰 الإجمالي: ${formatNum(total)} د.ع", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Green)
                Spacer(Modifier.height(6.dp))
                Text("🗓 أول دفعة: $first", fontSize = 13.5.sp)
                Spacer(Modifier.height(6.dp))
                Text("🗓 آخر دفعة: $last", fontSize = 13.5.sp)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("حسناً") }
            }
        }
    }
}

@Composable
fun DeletePeriodDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("🗑 حذف الكشف", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("هل أنت متأكد من حذف كشف: $name ؟", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) { Text("حذف") }
                }
            }
        }
    }
}

@Composable
fun NewPeriodDialog(title: String, subtitle: String, buttonText: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
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
                            if (name.isBlank()) return@Button
                            onOpen(name.trim())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) { Text(buttonText) }
                }
            }
        }
    }
}
