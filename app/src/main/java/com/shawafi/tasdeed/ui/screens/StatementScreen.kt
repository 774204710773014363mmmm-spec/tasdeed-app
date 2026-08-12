package com.shawafi.tasdeed.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StatementSort(val label: String) {
    DATE("🗓 الأحدث أولاً"),
    NAME("🔤 أبجدي (أ-ي)"),
    METER("🔢 رقم العداد (تصاعدي)")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatementScreen(
    vm: AppViewModel,
    name: String,
    idx: Int,
    kind: String = "archive",
    onBack: () -> Unit
) {
    val isCurrent = idx < 0
    val isMy = kind == "my"
    val currentPayments by vm.currentPayments.collectAsState()
    val periods by vm.periods.collectAsState()
    val myCur by vm.myAccountPayments.collectAsState()
    val myPeriods by vm.myPeriods.collectAsState()
    val list = if (isMy)
        (if (isCurrent) myCur else myPeriods.getOrNull(idx)?.payments ?: emptyList())
    else
        (if (isCurrent) currentPayments else periods.getOrNull(idx)?.payments ?: emptyList())

    var sortMode by remember { mutableStateOf(StatementSort.DATE) }
    val grouped = remember(list, sortMode, isMy) {
        if (isMy) {
            // كل دفعة = صف مستقل (حساباتي ليست بأسماء مشتركين)
            val rows = list.map { p ->
                ReportExporter.SubGroup(
                    key = p.localId,
                    name = p.note.ifEmpty { "دفعة" },
                    meter = p.meterNumber.ifEmpty { "" },
                    num = "",
                    total = p.amount,
                    latestDate = p.paymentDate
                ).apply { ids.add(p.localId) }
            }
            when (sortMode) {
                StatementSort.NAME -> rows.sortedBy { it.name }
                StatementSort.METER -> rows.sortedBy { it.meter.toLongOrNull() ?: Long.MAX_VALUE }
                StatementSort.DATE -> rows.sortedByDescending { it.latestDate }
            }
        } else {
            val g = ReportExporter.groupPayments(list)
            when (sortMode) {
                StatementSort.NAME -> g.sortedBy { it.name }
                StatementSort.METER -> g.sortedBy { it.meter.toLongOrNull() ?: Long.MAX_VALUE }
                StatementSort.DATE -> g.sortedByDescending { it.latestDate }
            }
        }
    }
    val total = grouped.sumOf { it.total }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var exporting by remember { mutableStateOf(false) }
    var dlOpen by remember { mutableStateOf(false) }
    var shOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var editedAmounts by remember { mutableStateOf<MutableMap<String, Double>>(mutableMapOf()) }
    var editTarget by remember { mutableStateOf<ReportExporter.SubGroup?>(null) }
    var subEdits by remember { mutableStateOf<MutableMap<String, Double>>(mutableMapOf()) }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "pdf") "application/pdf" else "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "مشاركة الكشف"))
    }

    fun buildFile(ext: String): File? {
        return try {
            val f = File(ctx.cacheDir, "كشف_${name.replace(" ", "_").replace("/", "_")}.$ext")
            val os = java.io.FileOutputStream(f)
            if (ext == "pdf") ReportExporter.exportPdf(os, vm, name, list, sortMode)
            else ReportExporter.exportExcel(os, vm, name, list, sortMode)
            os.close()
            f
        } catch (e: Exception) { null }
    }

    fun doShare(ext: String) {
        exporting = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { buildFile(ext) }
            exporting = false
            if (file != null) shareFile(file)
            else vm.toast("❌ فشل إنشاء الملف", true)
        }
    }

    // نافذة الحفظ في الجوال (SAF)
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val ok = withContext(Dispatchers.IO) {
                try {
                    val os = ctx.contentResolver.openOutputStream(uri)
                    if (os == null) false else {
                        ReportExporter.exportPdf(os, vm, name, list, sortMode)
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
                        ReportExporter.exportExcel(os, vm, name, list, sortMode)
                        os.close(); true
                    }
                } catch (e: Exception) { false }
            }
            exporting = false
            if (ok) vm.toast("✅ تم حفظ ملف Excel")
            else vm.toast("❌ فشل حفظ الملف", true)
        }
    }

    fun doDownload(ext: String) {
        if (ext == "pdf") savePdf.launch("كشف_${name.replace(" ", "_").replace("/", "_")}.pdf")
        else saveExcel.launch("كشف_${name.replace(" ", "_").replace("/", "_")}.xls")
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            modifier = Modifier.background(GreenBrush),
            title = {
                Column {
                    Text(if (isCurrent) "📋 $name" else "📁 $name", fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${grouped.size} مشترك | ${formatNum(total)} د.ع | ${sortMode.label}", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            },
            actions = {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.Sort, contentDescription = "ترتيب", tint = Color.White) }
                IconButton(onClick = { dlOpen = true }, enabled = !exporting && grouped.isNotEmpty()) {
                    Icon(Icons.Filled.Download, contentDescription = "تنزيل", tint = Color.White)
                }
                IconButton(onClick = { shOpen = true }, enabled = !exporting && grouped.isNotEmpty()) {
                    Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, actionIconContentColor = Color.White)
        )

        // قائمة سفلية (ModalBottomSheet) للترتيب
        if (sortOpen) {
            ModalBottomSheet(onDismissRequest = { sortOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                    Text("🔃 ترتيب الكشف", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    StatementSort.entries.forEach { s ->
                        ListItem(
                            headlineContent = { Text(s.label, fontWeight = if (s == sortMode) FontWeight.Bold else FontWeight.Normal) },
                            trailingContent = { if (s == sortMode) Icon(Icons.Filled.Check, contentDescription = "محدد", tint = Green) },
                            modifier = Modifier.clickable { sortMode = s; sortOpen = false }
                        )
                    }
                }
            }
        }

        // قائمة سفلية (ModalBottomSheet) للتنزيل فقط
        if (dlOpen) {
            ModalBottomSheet(onDismissRequest = { dlOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                    Text("💾 تنزيل $name", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    ListItem(
                        headlineContent = { Text("📄 تنزيل PDF") },
                        supportingContent = { Text("حفظ في الجوال (اختر المكان)") },
                        modifier = Modifier.clickable { dlOpen = false; doDownload("pdf") }
                    )
                    ListItem(
                        headlineContent = { Text("📊 تنزيل Excel") },
                        supportingContent = { Text("حفظ في الجوال (اختر المكان)") },
                        modifier = Modifier.clickable { dlOpen = false; doDownload("xls") }
                    )
                }
            }
        }

        // قائمة سفلية (ModalBottomSheet) للمشاركة فقط
        if (shOpen) {
            ModalBottomSheet(onDismissRequest = { shOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                    Text("📤 مشاركة $name", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
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

        if (grouped.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("📦 لا توجد مدفوعات في هذا الكشف", color = Color.Gray)
            }
        } else if (!editMode) {
            // جدول كامل بكل العمليات - أعمدة متسعة لعرض الأسماء كاملة
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Green).padding(vertical = 10.dp, horizontal = 8.dp)) {
                        Text("#", Modifier.width(28.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text(if (isMy) "الملاحظة" else "المشترك", Modifier.weight(1.4f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("العداد", Modifier.weight(0.9f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("آخر تاريخ", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("الإجمالي", Modifier.weight(0.8f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                itemsIndexed(grouped, key = { _, s -> s.key + s.name }) { i, s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (i % 2 == 0) Color(0xFFF0F7FC) else Color.White)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { if (!isMy) editTarget = s }
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text("${i + 1}", Modifier.width(28.dp), fontSize = 12.sp, color = Color.Gray)
                        Text(s.name.ifEmpty { "-" }, Modifier.weight(1.4f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.meter.ifEmpty { "-" }, Modifier.weight(0.9f), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.latestDate, Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatNum(s.total), Modifier.weight(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green, textAlign = TextAlign.End, maxLines = 1)
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                }
            }
        } else {
            // وضع التعديل الكامل
            val raw = if (isMy)
                (if (isCurrent) myCur else myPeriods.getOrNull(idx)?.payments ?: emptyList())
            else
                (if (isCurrent) currentPayments else periods.getOrNull(idx)?.payments ?: emptyList())
            Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(raw, key = { _, p -> p.localId }) { _, p ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editedAmounts[p.localId]?.let { formatNum(it) } ?: formatNum(p.amount),
                                onValueChange = { editedAmounts[p.localId] = it.toDoubleOrNull() ?: 0.0 },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(if (isMy) p.note.ifEmpty { "دفعة" } else p.subscriberName, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("📅 ${p.paymentDate}", fontSize = 11.sp, color = Color.Gray)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                    }
                }
                Text("💾 الإجمالي: ${formatNum(editedAmounts.values.sum())} د.ع", fontSize = 13.sp, color = Green, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }

        // الإجمالي الكلي - مثبت أسفل الشاشة دائماً (حتى لو الأسماء قليلة)
        if (grouped.isNotEmpty() && !editMode) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الإجمالي الكلي:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${formatNum(total)} د.ع", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Green)
            }
        }

        if (!editMode && grouped.isNotEmpty()) {
            OutlinedButton(
                onClick = { editMode = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(46.dp)
            ) { Text("✏️ تعديل المبالغ") }
        } else if (editMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { editMode = false }, modifier = Modifier.weight(1f).height(46.dp)) { Text("إلغاء") }
                Button(
                    onClick = {
                        if (isMy) vm.saveEditedMyPeriod(isCurrent, idx, editedAmounts.keys.toSet(), editedAmounts)
                        else vm.saveEditedPeriod(isCurrent, idx, editedAmounts.keys.toSet(), editedAmounts)
                        editMode = false
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("💾 حفظ") }
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

    // تعديل دفعة مشترك واحد (ضغطة مطولة)
    editTarget?.let { target ->
        val raw = if (isMy)
            (if (isCurrent) myCur else myPeriods.getOrNull(idx)?.payments ?: emptyList())
        else
            (if (isCurrent) currentPayments else periods.getOrNull(idx)?.payments ?: emptyList())
        val subPays = if (isMy) raw.filter { it.localId == target.key }
            else raw.filter { it.subscriberId.ifEmpty { it.meterNumber.ifEmpty { it.subscriberName } } == target.key }
        Dialog(onDismissRequest = { editTarget = null }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp).heightIn(max = 480.dp)) {
                    Text("✏️ تعديل: ${target.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${subPays.size} دفعة", fontSize = 11.5.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    if (subPays.isEmpty()) {
                        Text("لا توجد دفعات", color = Color.Gray)
                    } else {
                        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                            itemsIndexed(subPays, key = { _, p -> p.localId }) { _, p ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = subEdits[p.localId]?.let { formatNum(it) } ?: formatNum(p.amount),
                                        onValueChange = { subEdits[p.localId] = it.toDoubleOrNull() ?: 0.0 },
                                        singleLine = true,
                                        modifier = Modifier.width(110.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("📅 ${p.paymentDate}", modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { editTarget = null }, modifier = Modifier.weight(1f).height(46.dp)) { Text("إلغاء") }
                        Button(
                            onClick = {
                                if (isMy) vm.saveEditedMyPeriod(isCurrent, idx, subEdits.keys.toSet(), subEdits)
                                else vm.saveEditedPeriod(isCurrent, idx, subEdits.keys.toSet(), subEdits)
                                editTarget = null
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("💾 حفظ") }
                    }
                }
            }
        }
    }
}
