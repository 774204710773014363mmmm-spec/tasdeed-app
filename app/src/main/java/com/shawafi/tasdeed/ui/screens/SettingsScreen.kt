package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.biometric.BiometricManager
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenDark

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
    val devMode by vm.devMode.collectAsState()
    val mergeOps by vm.mergeOps.collectAsState()
    var pendingVisible by remember { mutableStateOf(false) }
    var smsPassOpen by remember { mutableStateOf(false) }
    var smsPass by remember { mutableStateOf("") }
    var smsPassHidden by remember { mutableStateOf(true) }
    var devPassOpen by remember { mutableStateOf(false) }
    var devPass by remember { mutableStateOf("") }
    var devPassHidden by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "الإعدادات")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ---------- المظهر ----------
            item { SectionLabel("🎨 المظهر") }
            item {
                SettingsGroup {
                    ToggleRow(
                        title = "الوضع الداكن",
                        subtitle = if (darkTheme) "🌙 مفعّل" else "☀️ فاتح",
                        checked = darkTheme,
                        onToggle = { vm.setTheme(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    DropdownRow(
                        title = "حجم الخط",
                        value = "$fontScale%",
                        options = listOf("80%", "100%", "120%", "140%", "160%"),
                        onSelect = { vm.setFontScale(listOf(80, 100, 120, 140, 160)[it]) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    DropdownRow(
                        title = "معدل التحديث",
                        value = "$uiFps فريم/ث",
                        options = listOf("30 فريم/ث", "60 فريم/ث", "90 فريم/ث", "120 فريم/ث"),
                        onSelect = { vm.setUiFps(listOf(30, 60, 90, 120)[it]) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ToggleRow(
                        title = "دمج العمليات",
                        subtitle = if (mergeOps) "مدفوعات نفس المشترك تُدمج في صف واحد" else "كل عملية تظهر كدفعة مستقلة",
                        checked = mergeOps,
                        onToggle = {
                            vm.setMergeOps(it)
                            vm.toast(if (it) "✅ سيتم دمج العمليات في الكشوفات والملفات" else "كل عملية ستُطبع وتُشارك منفصلة")
                        }
                    )
                }
            }

            // ---------- الأمان ----------
            item { SectionLabel("🔒 الأمان") }
            item {
                SettingsGroup {
                    ToggleRow(
                        title = "الدخول بالبصمة",
                        subtitle = if (bioEnabled) "✅ مفعّلة" else "غير مفعّلة",
                        checked = bioEnabled,
                        onToggle = { on ->
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

            // ---------- المزامنة ----------
            item { SectionLabel("📶 المزامنة") }
            item {
                SettingsGroup {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("الدفعات المعلقة", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "مدفوعات: ${pendingPayments.size} | دفعات حرة: ${pendingFree.size}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            TextButton(onClick = { pendingVisible = !pendingVisible }) {
                                Text(if (pendingVisible) "إخفاء" else "عرض")
                            }
                        }
                        Button(
                            onClick = { vm.syncPendingPayments(); vm.syncFreePayments() },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("🔄 مزامنة الآن", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (pendingVisible && (pendingPayments.isNotEmpty() || pendingFree.isNotEmpty())) {
                item {
                    SettingsGroup {
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
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

            // ---------- الحساب ----------
            item { SectionLabel("👤 الحساب") }
            item {
                SettingsGroup {
                    Column(Modifier.padding(14.dp)) {
                        Text("المستخدم", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(vm.user.value ?: "-", fontSize = 14.sp, color = Color.Gray)
                        Text(vm.branchName.value, fontSize = 14.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.logout() },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) { Text("🚪 تسجيل الخروج", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // ---------- وضع المطور ----------
            item { SectionLabel("⚙️ وضع المطور") }
            item {
                SettingsGroup {
                    ToggleRow(
                        title = "وضع المطور",
                        subtitle = if (devMode) "🛠️ مفعّل (ملخص الحسابات + كشوفات المحصلين)" else "غير مفعّل",
                        checked = devMode,
                        onToggle = { on ->
                            if (on) {
                                devPass = ""
                                devPassOpen = true
                            } else {
                                vm.setDevMode(false)
                                vm.toast("تم إيقاف وضع المطور")
                            }
                        }
                    )
                    if (devMode) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        var resetConfirm by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text("🗑️ حذف المبالغ المسددة (بداية شهر جديد)", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                            supportingContent = { Text("يصفّر العداد ويبدأ من الصفر — لا يحذف الدفعات الأرشيفية", fontSize = 11.sp) },
                            modifier = Modifier.clickable { resetConfirm = true }
                        )
                        if (resetConfirm) {
                            Dialog(onDismissRequest = { resetConfirm = false }) {
                                Surface(shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(20.dp)) {
                                        Text("🗑️ تصفير المبالغ المسددة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("سيتم حذف جميع إجماليات الدفعات المسددة لكل المشتركين (العداد الأخضر).", fontSize = 12.sp, color = Color.Gray)
                                        Text("الدفعات الأرشيفية نفسها لا تُمس. ابدأ العد من جديد لشهر جديد.", fontSize = 12.sp, color = Color.Gray)
                                        Spacer(Modifier.height(18.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { resetConfirm = false }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                                            Button(
                                                onClick = { vm.resetPaidTotals(); resetConfirm = false },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                            ) { Text("🗑️ تصفير", fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- إضافات ----------
            item { SectionLabel("📨 إضافات") }
            item {
                SettingsGroup {
                    Column(Modifier.padding(4.dp)) {
                        ListItem(
                            headlineContent = { Text("📨 فواتير SMS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                            supportingContent = { Text("الدخول إلى قسم إرسال الفواتير", fontSize = 12.sp) },
                            trailingContent = { Text("▶", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.clickable { smsPass = ""; smsPassOpen = true }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // نافذة كلمة مرور وضع المطور (تفعيل محلي على هذا الجهاز فقط)
    if (devPassOpen) {
        Dialog(onDismissRequest = { devPassOpen = false; devPass = "" }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("🔧 أدخل كلمة مرور وضع المطور", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("التفعيل محلي على هذا الجهاز فقط ولا يُزامن مع الأجهزة الأخرى", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = devPass,
                        onValueChange = { devPass = it },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        visualTransformation = if (devPassHidden) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { devPassHidden = !devPassHidden }) {
                                Icon(
                                    if (devPassHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "إظهار"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { devPassOpen = false; devPass = "" },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("إلغاء") }
                        Button(
                            onClick = {
                                devPassOpen = false
                                if (devPass.trim() == "77352085333") {
                                    devPass = ""
                                    vm.setDevMode(true)
                                    vm.toast("✅ تم تفعيل وضع المطور")
                                } else {
                                    devPass = ""
                                    vm.toast("❌ كلمة المرور غير صحيحة", true)
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("تفعيل") }
                    }
                }
            }
        }
    }
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

@Composable
fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenDark, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
fun DropdownRow(title: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.height(42.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(value, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("▾", fontSize = 11.sp, color = Color.Gray)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontSize = 13.sp) },
                        onClick = { expanded = false; onSelect(i) }
                    )
                }
            }
        }
    }
}