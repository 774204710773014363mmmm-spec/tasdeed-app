package com.shawafi.tasdeed.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementScreen(
    vm: AppViewModel,
    name: String,
    idx: Int,
    onBack: () -> Unit
) {
    val isCurrent = idx < 0
    val currentPayments by vm.currentPayments.collectAsState()
    val periods by vm.periods.collectAsState()
    val list = if (isCurrent) currentPayments else periods.getOrNull(idx)?.payments ?: emptyList()
    val grouped = remember(list) { ReportExporter.groupPayments(list).sortedByDescending { it.latestDate } }
    val total = grouped.sumOf { it.total }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var exporting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var editedAmounts by remember { mutableStateOf<MutableMap<String, Double>>(mutableMapOf()) }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "pdf") "application/pdf" else "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "مشاركة الكشف"))
    }

    fun doExport(ext: String) {
        exporting = true
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val f = File(ctx.cacheDir, "كشف_${name.replace(" ", "_").replace("/", "_")}.$ext")
                    val os = java.io.FileOutputStream(f)
                    if (ext == "pdf") ReportExporter.exportPdf(os, vm, name, list)
                    else ReportExporter.exportExcel(os, vm, name, list)
                    os.close()
                    f
                } catch (e: Exception) { null }
            }
            exporting = false
            if (file != null) shareFile(file)
            else vm.toast("❌ فشل إنشاء الملف", true)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = {
                Column {
                    Text(if (isCurrent) "📋 $name" else "📁 $name", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${grouped.size} مشترك | ${formatNum(total)} د.ع", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            },
            actions = {
                IconButton(onClick = { menuOpen = true }, enabled = !exporting && grouped.isNotEmpty()) {
                    Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White, actionIconContentColor = Color.White)
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("📄 مشاركة PDF") }, onClick = { menuOpen = false; doExport("pdf") })
            DropdownMenuItem(text = { Text("📊 مشاركة Excel") }, onClick = { menuOpen = false; doExport("xls") })
        }

        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("📦 لا توجد مدفوعات في هذا الكشف", color = Color.Gray)
            }
        } else if (!editMode) {
            // جدول كامل بكل العمليات
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Green).padding(vertical = 9.dp, horizontal = 8.dp)) {
                        Text("#", Modifier.width(30.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text("المشترك", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text("العداد", Modifier.width(76.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center)
                        Text("آخر تاريخ", Modifier.width(84.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.Center)
                        Text("الإجمالي", Modifier.width(78.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, textAlign = TextAlign.End)
                    }
                }
                itemsIndexed(grouped, key = { _, s -> s.key }) { i, s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (i % 2 == 0) Color(0xFFF6F9F7) else Color.White)
                            .padding(vertical = 9.dp, horizontal = 8.dp)
                    ) {
                        Text("${i + 1}", Modifier.width(30.dp), fontSize = 12.sp, color = Color.Gray)
                        Text(s.name.ifEmpty { "-" }, Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.meter.ifEmpty { "-" }, Modifier.width(76.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                        Text(s.latestDate, Modifier.width(84.dp), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        Text(formatNum(s.total), Modifier.width(78.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("الإجمالي الكلي:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${formatNum(total)} د.ع", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Green)
                    }
                }
            }
        } else {
            // وضع التعديل
            val raw = if (isCurrent) currentPayments else periods.getOrNull(idx)?.payments ?: emptyList()
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
                            Text(p.subscriberName, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Text("📅 ${p.paymentDate}", fontSize = 11.sp, color = Color.Gray)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                    }
                }
                Text("💾 الإجمالي: ${formatNum(editedAmounts.values.sum())} د.ع", fontSize = 13.sp, color = Green, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }

        if (!editMode) {
            OutlinedButton(
                onClick = { editMode = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(46.dp)
            ) { Text("✏️ تعديل المبالغ") }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { editMode = false }, modifier = Modifier.weight(1f).height(46.dp)) { Text("إلغاء") }
                Button(
                    onClick = {
                        vm.saveEditedPeriod(isCurrent, idx, editedAmounts.keys.toSet(), editedAmounts)
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
}
