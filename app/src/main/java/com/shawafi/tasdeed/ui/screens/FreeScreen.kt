package com.shawafi.tasdeed.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FreeScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit,
    onOpenStatement: (String, Int, String) -> Unit,
    onSettings: () -> Unit = {}
) {
    var showAdd by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var dlOpen by remember { mutableStateOf(false) }
    var shOpen by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<Int?>(null) }
    var renameTarget by remember { mutableStateOf<Int?>(null) }
    var infoTarget by remember { mutableStateOf<Int?>(null) }
    var deleteTarget by remember { mutableStateOf<Int?>(null) }
    val myPayments by vm.myAccountPayments.collectAsState()
    val myPeriods by vm.myPeriods.collectAsState()
    val total = myPayments.sumOf { it.amount }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    // حفظ محلي (نافذة النظام)
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val ok = withContext(Dispatchers.IO) {
                try {
                    val os = ctx.contentResolver.openOutputStream(uri)
                    if (os == null) false else {
                        ReportExporter.exportPdf(os, vm, "حساباتي", myPayments)
                        os.close(); true
                    }
                } catch (e: Exception) { false }
            }
            exporting = false
            if (ok) vm.toast("✅ تم حفظ ملف PDF")
            else vm.toast("❌ فشل حفظ الملف", true)
        }
    }
    val saveExcel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.ms-excel")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val ok = withContext(Dispatchers.IO) {
                try {
                    val os = ctx.contentResolver.openOutputStream(uri)
                    if (os == null) false else {
                        ReportExporter.exportExcel(os, vm, "حساباتي", myPayments)
                        os.close(); true
                    }
                } catch (e: Exception) { false }
            }
            exporting = false
            if (ok) vm.toast("✅ تم حفظ ملف Excel")
            else vm.toast("❌ فشل حفظ الملف", true)
        }
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "pdf") "application/pdf" else "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "مشاركة كشف حساباتي"))
    }

    fun doShare(ext: String) {
        exporting = true
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val f = File(ctx.cacheDir, "حساباتي.$ext")
                    val os = java.io.FileOutputStream(f)
                    if (ext == "pdf") ReportExporter.exportPdf(os, vm, "حساباتي", myPayments)
                    else ReportExporter.exportExcel(os, vm, "حساباتي", myPayments)
                    os.close(); f
                } catch (e: Exception) { null }
            }
            exporting = false
            if (file != null) shareFile(file)
            else vm.toast("❌ فشل إنشاء الملف", true)
        }
    }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "حساباتي", onRefresh = { vm.reloadMyAccount() }, onSettings = onSettings)

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💼 الحساب الحالي", fontWeight = FontWeight.Bold)
                        Text("${myPayments.size} دفعة | ${formatNum(total)} د.ع", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionPill(
                                icon = Icons.Filled.Share,
                                label = "مشاركة",
                                onClick = { shOpen = true },
                                enabled = myPayments.isNotEmpty() && !exporting,
                                modifier = Modifier.weight(1f)
                            )
                            ActionPill(
                                icon = Icons.Filled.FileDownload,
                                label = "تنزيل",
                                onClick = { dlOpen = true },
                                enabled = myPayments.isNotEmpty() && !exporting,
                                modifier = Modifier.weight(1f)
                            )
                            ActionPill(
                                icon = Icons.Filled.AddCircle,
                                label = "تسجيل دفعة",
                                onClick = { showAdd = true },
                                highlight = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (myPayments.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onOpenStatement("حساباتي الحالية", -1, "my") },
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
                ) { Text("➕ فتح كشف حساباتي جديد", fontWeight = FontWeight.Bold) }
            }
            item { Text("💼 الكشوفات السابقة", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(myPeriods, key = { it.name + it.createdAt }) { p ->
                val pIdx = myPeriods.indexOf(p)
                Card(modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onOpenStatement(p.name, pIdx, "my") },
                    onLongClick = { menuTarget = pIdx }
                )) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📁 ${p.name}", fontWeight = FontWeight.SemiBold)
                            Text("${p.payments.size} دفعة | ${formatNum(p.payments.sumOf { it.amount })} د.ع", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("🗓 ${p.createdAt}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            if (myPeriods.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { Text("لا توجد كشوفات سابقة", color = Color.Gray) } }
            }
        }

        if (exporting) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("🔄 جاري إنشاء الملف...", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }

    // قائمة سفلية للمشاركة فقط
    if (shOpen) {
        ModalBottomSheet(onDismissRequest = { shOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("📤 مشاركة كشف حساباتي", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                ListItem(
                    headlineContent = { Text("📄 مشاركة PDF") },
                    modifier = Modifier.clickable { shOpen = false; doShare("pdf") }
                )
                ListItem(
                    headlineContent = { Text("📊 مشاركة Excel") },
                    modifier = Modifier.clickable { shOpen = false; doShare("xls") }
                )
            }
        }
    }

    // قائمة سفلية للتنزيل فقط
    if (dlOpen) {
        ModalBottomSheet(onDismissRequest = { dlOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("💾 تنزيل كشف حساباتي", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                ListItem(
                    headlineContent = { Text("📄 تنزيل PDF") },
                    supportingContent = { Text("حفظ في الجوال (اختر المكان)") },
                    modifier = Modifier.clickable { dlOpen = false; savePdf.launch("حساباتي.pdf") }
                )
                ListItem(
                    headlineContent = { Text("📊 تنزيل Excel") },
                    supportingContent = { Text("حفظ في الجوال (اختر المكان)") },
                    modifier = Modifier.clickable { dlOpen = false; saveExcel.launch("حساباتي.xls") }
                )
            }
        }
    }

    if (showAdd) {
        AddMyPaymentDialog(vm, onDismiss = { showAdd = false })
    }

    // قائمة الضغطة المطولة على كشف سابق
    val menuIdx = menuTarget
    if (menuIdx != null) {
        val p = myPeriods.getOrNull(menuIdx)
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
        val p = myPeriods.getOrNull(renIdx)
        if (p != null) {
            RenamePeriodDialog(p.name, onSave = { vm.renameMyPeriod(renIdx, it) }) { renameTarget = null }
        } else {
            renameTarget = null
        }
    }

    // معلومات الكشف
    val infIdx = infoTarget
    if (infIdx != null) {
        val p = myPeriods.getOrNull(infIdx)
        if (p != null) {
            PeriodInfoDialog(vm, p) { infoTarget = null }
        } else {
            infoTarget = null
        }
    }

    // تأكيد الحذف
    val delIdx = deleteTarget
    if (delIdx != null) {
        val p = myPeriods.getOrNull(delIdx)
        if (p != null) {
            DeletePeriodDialog(p.name, onConfirm = { vm.deleteMyPeriod(delIdx) }) { deleteTarget = null }
        } else {
            deleteTarget = null
        }
    }

    // فتح كشف جديد
    if (showNew) {
        NewPeriodDialog(
            title = "➕ فتح كشف حساباتي جديد",
            subtitle = "سيتم نقل دفعات الحساب الحالي إلى الكشف الجديد",
            buttonText = "فتح",
            onOpen = { vm.newMyPeriod(it) },
            onDismiss = { showNew = false }
        )
    }
}

@Composable
fun AddMyPaymentDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("💼 تسجيل دفعة - حساباتي", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                    label = { Text("الملاحظة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) { vm.toast("أدخل مبلغ صحيح", true); return@Button }
                            vm.addMyAccountPayment(amt, note.trim())
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