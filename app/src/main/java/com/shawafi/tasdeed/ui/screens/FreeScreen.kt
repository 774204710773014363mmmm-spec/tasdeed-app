package com.shawafi.tasdeed.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.shawafi.tasdeed.data.PaymentRecord
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onNav: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val myPayments by vm.myAccountPayments.collectAsState()
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
        TopBar(vm, "حساباتي", onRefresh = { vm.reloadMyAccount() })

        if (myPayments.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💼", fontSize = 40.sp)
                    Text("لا توجد دفعات بعد", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            // جدول كامل بكل عمليات الحساب
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Green).padding(vertical = 10.dp, horizontal = 8.dp)) {
                        Text("#", Modifier.width(28.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text("التاريخ", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text("ملاحظة", Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center)
                        Text("المبلغ", Modifier.weight(0.9f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.End)
                    }
                }
                items(myPayments.sortedByDescending { it.createdAt }, key = { it.localId }) { p ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (myPayments.indexOf(p) % 2 == 0) Color(0xFFF6F9F7) else Color.White)
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text("${myPayments.indexOf(p) + 1}", Modifier.width(28.dp), fontSize = 12.sp, color = Color.Gray)
                        Text(p.paymentDate, Modifier.weight(1f), fontSize = 12.sp)
                        Text(p.note.ifEmpty { "-" }, Modifier.weight(1.2f), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        Text(formatNum(p.amount), Modifier.weight(0.9f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Green, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                }
            }
        }

        // الإجمالي مثبت أسفل الشاشة دائماً
        if (myPayments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الإجمالي الكلي:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${formatNum(total)} د.ع", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Green)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { showAdd = true },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("➕ تسجيل دفعة", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = myPayments.isNotEmpty() && !exporting
            ) { Text("📤 مشاركة / حفظ") }
        }

        if (exporting) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("🔄 جاري إنشاء الملف...", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }

    // قائمة منبثقة من الأسفل (ModalBottomSheet)
    if (menuOpen) {
        ModalBottomSheet(onDismissRequest = { menuOpen = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("📤 كشف حساباتي", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                ListItem(
                    headlineContent = { Text("📄 مشاركة PDF") },
                    modifier = Modifier.clickable { menuOpen = false; doShare("pdf") }
                )
                ListItem(
                    headlineContent = { Text("📊 مشاركة Excel") },
                    modifier = Modifier.clickable { menuOpen = false; doShare("xls") }
                )
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text("💾 حفظ PDF في الجوال") },
                    modifier = Modifier.clickable { menuOpen = false; savePdf.launch("حساباتي.pdf") }
                )
                ListItem(
                    headlineContent = { Text("📁 حفظ Excel في الجوال") },
                    modifier = Modifier.clickable { menuOpen = false; saveExcel.launch("حساباتي.xls") }
                )
            }
        }
    }

    if (showAdd) {
        AddMyPaymentDialog(vm, onDismiss = { showAdd = false })
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