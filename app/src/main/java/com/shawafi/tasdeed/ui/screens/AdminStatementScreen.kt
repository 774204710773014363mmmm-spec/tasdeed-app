package com.shawafi.tasdeed.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.shawafi.tasdeed.data.PaymentRecord
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// شاشة فتح كشف محصل من وضع المطور: عرض + تعديل + تصدير + حفظ يصل للجميع
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdminStatementScreen(
    vm: AppViewModel,
    branchKey: String,
    title: String,
    payments: List<PaymentRecord>,
    onBack: () -> Unit,
    onSave: (List<PaymentRecord>) -> Unit
) {
    var items by remember { mutableStateOf(payments.toMutableList()) }
    var sortMode by remember { mutableStateOf(StatementSort.DATE) }
    val mergeOps by vm.mergeOps.collectAsState()

    val grouped = remember(items, sortMode, mergeOps) {
        if (!mergeOps) {
            val rows = items.map { p ->
                ReportExporter.SubGroup(
                    key = p.localId,
                    name = p.subscriberName,
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
            val g = ReportExporter.groupPayments(items)
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
    var sortOpen by remember { mutableStateOf(false) }
    var dlOpen by remember { mutableStateOf(false) }
    var shOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ReportExporter.SubGroup?>(null) }
    var subEdits by remember { mutableStateOf<MutableMap<String, Double>>(mutableMapOf()) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val ok = withContext(Dispatchers.IO) {
                try {
                    val os = ctx.contentResolver.openOutputStream(uri)
                    if (os == null) false else {
                        ReportExporter.exportPdf(os, vm, title, items, sortMode, isMy = false, merge = mergeOps)
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
                        ReportExporter.exportExcel(os, vm, title, items, sortMode, isMy = false, merge = mergeOps)
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
        if (ext == "pdf") savePdf.launch("كشف_${title.replace(" ", "_").replace("/", "_")}.pdf")
        else saveExcel.launch("كشف_${title.replace(" ", "_").replace("/", "_")}.xls")
    }

    fun buildCacheFile(ext: String): File? = try {
        val f = File(ctx.cacheDir, "كشف_${branchKey}_${title.replace(" ", "_").replace("/", "_")}.$ext")
        val os = java.io.FileOutputStream(f)
        if (ext == "pdf") ReportExporter.exportPdf(os, vm, title, items, sortMode, isMy = false, merge = mergeOps)
        else ReportExporter.exportExcel(os, vm, title, items, sortMode, isMy = false, merge = mergeOps)
        os.close()
        f
    } catch (e: Exception) { null }

    fun doShare(ext: String) {
        exporting = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { buildCacheFile(ext) }
            exporting = false
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = if (ext == "pdf") "application/pdf" else "application/vnd.ms-excel"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(android.content.Intent.createChooser(intent, "مشاركة الكشف"))
            } else vm.toast("❌ فشل إنشاء الملف", true)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            modifier = Modifier.background(GreenBrush),
            title = {
                Column {
                    Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                    Text("${grouped.size} مشترك | ${formatNum(total)} د.ع", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
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
                IconButton(onClick = { onSave(items) }) { Icon(Icons.Filled.Save, contentDescription = "حفظ", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, actionIconContentColor = Color.White)
        )

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
        if (dlOpen) {
            ModalBottomSheet(onDismissRequest = { dlOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                    Text("💾 تنزيل $title", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    ListItem(
                        headlineContent = { Text("📄 تنزيل PDF") },
                        modifier = Modifier.clickable { dlOpen = false; doDownload("pdf") }
                    )
                    ListItem(
                        headlineContent = { Text("📊 تنزيل Excel") },
                        modifier = Modifier.clickable { dlOpen = false; doDownload("xls") }
                    )
                }
            }
        }
        if (shOpen) {
            ModalBottomSheet(onDismissRequest = { shOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                    Text("📤 مشاركة $title", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
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
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Green).padding(vertical = 10.dp, horizontal = 8.dp)) {
                        Text("#", Modifier.width(28.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text("المشترك", Modifier.weight(1.4f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                        Text("العداد", Modifier.weight(0.9f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1)
                        Text("آخر تاريخ", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1)
                        Text("الإجمالي", Modifier.weight(0.8f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
                itemsIndexed(grouped, key = { _, s -> s.key + s.name }) { i, s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(
                                if (i % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { editTarget = s }
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text("${i + 1}", Modifier.width(28.dp), fontSize = 12.sp, color = Color.Gray)
                        Text(s.name.ifEmpty { "-" }, Modifier.weight(1.4f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                        Text(s.meter.ifEmpty { "-" }, Modifier.weight(0.9f), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(s.latestDate, Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(formatNum(s.total), Modifier.weight(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green, textAlign = TextAlign.End, maxLines = 1)
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                }
            }
        }

        if (exporting) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("🔄 جاري إنشاء الملف...", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }

    // تعديل دفعة مشترك واحد (ضغطة مطولة)
    editTarget?.let { target ->
        val subPays = if (!mergeOps) items.filter { it.localId == target.key }
            else items.filter { it.subscriberId.ifEmpty { it.meterNumber.ifEmpty { it.subscriberName } } == target.key }
        Dialog(onDismissRequest = { editTarget = null }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp).heightIn(max = 480.dp)) {
                    Text("✏️ تعديل: ${target.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
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
                                    Column(Modifier.weight(1f)) {
                                        Text(p.subscriberName, fontSize = 12.sp, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                                        Text("📅 ${p.paymentDate}", fontSize = 10.5.sp, color = Color.Gray)
                                    }
                                }
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            items = items.filterNot { it.localId in subPays.map { it.localId } }.toMutableList()
                            editTarget = null
                            vm.toast("🗑️ تم حذف الدفعات - اضغط 💾 للحفظ")
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) { Text("🗑️ حذف هذه الدفعات", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { editTarget = null }, modifier = Modifier.weight(1f).height(46.dp)) { Text("إلغاء") }
                        Button(
                            onClick = {
                                val edited = subPays.mapNotNull { p ->
                                    val amt = subEdits[p.localId] ?: p.amount
                                    if (amt > 0) p.copy(amount = amt) else null
                                }
                                val editedIds = edited.map { it.localId }.toSet()
                                items = items.map { p ->
                                    if (p.localId in editedIds) edited.first { it.localId == p.localId }
                                    else if (subPays.any { it.localId == p.localId }) null
                                    else p
                                }.filterNotNull().toMutableList()
                                editTarget = null
                                vm.toast("✏️ عدّل الدفعات - اضغط 💾 للحفظ")
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("✔️ تعديل") }
                    }
                }
            }
        }
    }
}