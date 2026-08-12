package com.shawafi.tasdeed.ui.screens

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
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit,
    onOpenStatement: (String, Int) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    val currentPayments by vm.currentPayments.collectAsState()
    val periods by vm.periods.collectAsState()
    val currentTotal = currentPayments.sumOf { it.amount }
    val currentSubCount = remember(currentPayments) { ReportExporter.groupPayments(currentPayments).size }
    val periodSubCounts = remember(periods) { periods.map { ReportExporter.groupPayments(it.payments).size } }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الكشوفات", onRefresh = { vm.syncPendingPayments() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋 الكشف الحالي", fontWeight = FontWeight.Bold)
                        Text("${currentSubCount} مشترك | ${formatNum(currentTotal)} د.ع", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        if (currentPayments.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { onOpenStatement("الكشف الحالي", -1) },
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
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenStatement(p.name, periods.indexOf(p)) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📁 ${p.name}", fontWeight = FontWeight.SemiBold)
                            Text("${periodSubCounts.getOrElse(periods.indexOf(p)) { 0 }} مشترك | ${formatNum(p.payments.sumOf { it.amount })} د.ع", fontSize = 12.sp, color = Color.Gray)
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
