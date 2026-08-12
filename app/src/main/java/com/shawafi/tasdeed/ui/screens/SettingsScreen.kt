package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.biometric.BiometricManager
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit
) {
    val pendingPayments by vm.pendingPayments.collectAsState()
    val pendingFree by vm.pendingFreePayments.collectAsState()
    val darkTheme by vm.darkTheme.collectAsState()
    val uiFps by vm.uiFps.collectAsState()
    val fontScale by vm.fontScale.collectAsState()
    val bioEnabled by vm.bioEnabled.collectAsState()
    var pendingVisible by remember { mutableStateOf(false) }
    var smsPassOpen by remember { mutableStateOf(false) }
    var smsPass by remember { mutableStateOf("") }
    var smsPassHidden by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الإعدادات")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth().clickable { smsPass = ""; smsPassOpen = true }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📨 فواتير SMS", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("▶", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🎨 المظهر", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (darkTheme) "🌙 الوضع الداكن" else "☀️ الوضع الفاتح", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(checked = darkTheme, onCheckedChange = { vm.setTheme(it) })
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⚡ معدل التحديث (فريمات/ثانية)", fontWeight = FontWeight.Bold)
                        Text("كلما زاد، كانت الواجهة أكثر سلاسة", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60, 90, 120).forEach { fps ->
                                val selected = uiFps == fps
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.setUiFps(fps) },
                                    label = { Text("$fps", fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🔤 حجم الخط", fontWeight = FontWeight.Bold)
                        Text("مضاعف الحجم: ${fontScale}%", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(80, 100, 120, 140, 160).forEach { fs ->
                                val selected = fontScale == fs
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.setFontScale(fs) },
                                    label = { Text("$fs%", fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🔐 بصمة الجوال", fontWeight = FontWeight.Bold)
                        Text("سجّل الدخول ببصمة جهازك بدل كتابة كلمة المرور", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (bioEnabled) "✅ مفعّلة" else "غير مفعّلة",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                color = if (bioEnabled) Green else Color.Gray
                            )
                            Switch(
                                checked = bioEnabled,
                                onCheckedChange = { on ->
                                    if (on) {
                                        when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                                            BiometricManager.BIOMETRIC_SUCCESS -> {
                                                vm.setBioEnabled(true)
                                                vm.toast("✅ تم تفعيل الدخول بالبصمة")
                                            }
                                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                                vm.toast("⚠️ لا توجد بصمة مسجلة في الجوال", true)
                                            else ->
                                                vm.toast("❌ الجوال لا يدعم البصمة", true)
                                        }
                                    } else {
                                        vm.setBioEnabled(false)
                                        vm.toast("تم إيقاف الدخول بالبصمة")
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("👤 المستخدم", fontWeight = FontWeight.Bold)
                        Text(vm.user.value ?: "-", fontSize = 14.sp, color = Color.Gray)
                        Text(vm.branchName.value, fontSize = 14.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.logout() },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) { Text("🚪 تسجيل الخروج") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⏳ الدفعات المعلقة", fontWeight = FontWeight.Bold)
                        Text("مدفوعات: ${pendingPayments.size} | دفعات حرة: ${pendingFree.size}", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { vm.syncPendingPayments(); vm.syncFreePayments() },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("🔄 مزامنة الآن") }
                            OutlinedButton(
                                onClick = { pendingVisible = !pendingVisible },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("📋 عرض") }
                        }
                    }
                }
            }
            if (pendingVisible) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("📋 مدفوعات معلقة (${pendingPayments.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            pendingPayments.forEachIndexed { idx, p ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.subscriberName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("📅 ${p.paymentDate} | ${formatNum(p.amount)} د.ع", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    IconButton(onClick = { vm.deletePendingPayment(idx) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFDC2626))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                            Text("📋 دفعات حرة معلقة (${pendingFree.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            pendingFree.forEachIndexed { idx, fp ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(fp.beneficiary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("📅 ${fp.paymentDate} | ${formatNum(fp.amount)} د.ع", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        val l = pendingFree.toMutableList()
                                        if (idx in l.indices) l.removeAt(idx)
                                        vm.pendingFreePayments.value = l
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // نافذة كلمة مرور ميزة SMS (خاطئة ← تُغلق بصمت بدون أي رسالة)
    if (smsPassOpen) {
        Dialog(onDismissRequest = { smsPassOpen = false; smsPass = "" }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("🔒 أدخل كلمة المرور", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = smsPass,
                        onValueChange = { smsPass = it },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        visualTransformation = if (smsPassHidden) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { smsPassHidden = !smsPassHidden }) {
                                Icon(
                                    if (smsPassHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "إظهار"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { smsPassOpen = false; smsPass = "" },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("إلغاء") }
                        Button(
                            onClick = {
                                smsPassOpen = false
                                if (smsPass.trim() == "773520853") {
                                    smsPass = ""
                                    onNav("sms")
                                } else {
                                    smsPass = ""
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("دخول") }
                    }
                }
            }
        }
    }
}
