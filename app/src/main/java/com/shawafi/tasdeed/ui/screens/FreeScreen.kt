package com.shawafi.tasdeed.ui.screens

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
import com.shawafi.tasdeed.ui.theme.Amber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    val pendingFree by vm.pendingFreePayments.collectAsState()
    val freePayments by vm.freePayments.collectAsState()

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الدفعات الحرة", onRefresh = { vm.fetchFreePayments(); vm.syncFreePayments() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("➕ إضافة دفعة حرة", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                Text("💸 الدفعات الحرة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
            }
            items(freePayments, key = { it.localId }) { fp ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fp.beneficiary, fontWeight = FontWeight.SemiBold)
                            Text("📅 ${fp.paymentDate}", fontSize = 12.sp, color = Color.Gray)
                            if (fp.note.isNotEmpty()) Text(fp.note, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("${formatNum(fp.amount)} د.ع", fontWeight = FontWeight.Bold, color = Green)
                    }
                }
            }
            if (freePayments.isEmpty() && pendingFree.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد دفعات حرة بعد", color = Color.Gray)
                    }
                }
            }
            item {
                if (pendingFree.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("⏳ معلقة (${pendingFree.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(pendingFree, key = { it.localId }) { fp ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fp.beneficiary, fontWeight = FontWeight.SemiBold)
                            Text("📅 ${fp.paymentDate}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("${formatNum(fp.amount)} د.ع", fontWeight = FontWeight.Bold, color = Amber)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFreeDialog(vm, onDismiss = { showAdd = false })
    }
}

@Composable
fun AddFreeDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var beneficiary by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("➕ إضافة دفعة حرة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = beneficiary,
                    onValueChange = { beneficiary = it },
                    label = { Text("المستفيد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (beneficiary.isBlank()) { vm.toast("أدخل اسم المستفيد", true); return@Button }
                            if (amt <= 0) { vm.toast("أدخل مبلغ صحيح", true); return@Button }
                            vm.addFreePayment(beneficiary.trim(), amt, note.trim())
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
