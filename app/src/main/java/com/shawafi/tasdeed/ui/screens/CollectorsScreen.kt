package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.data.PaymentRecord
import com.shawafi.tasdeed.data.Period
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtClosed(ms: Long): String = try {
    SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ms))
} catch (e: Exception) {
    ""
}

// كشف مفتوح من كشوفات المحصلين للتعديل
private class AdminOpen(val branchKey: String, val branchName: String, val isCurrent: Boolean, val idx: Int)

@Composable
fun CollectorsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onSettings: () -> Unit = {}
) {
    var data by remember { mutableStateOf<List<Triple<String, List<PaymentRecord>, List<Period>>>>(emptyList()) }
    var branchKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var openTarget by remember { mutableStateOf<AdminOpen?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        scope.launch {
            val res: MutableList<Triple<String, List<PaymentRecord>, List<Period>>> = mutableListOf()
            withContext(Dispatchers.IO) {
                vm.repo.getBranches().entries.sortedBy { it.value.name }.forEach { (k, b) ->
                    try {
                        val arch = vm.repo.fetchArchive(k) ?: return@forEach
                        res.add(Triple(b.name, arch.first, arch.second))
                    } catch (e: Exception) {}
                }
            }
            branchKeys = res.map { it.first }
            data = res
            loading = false
            loaded = true
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.padding(padding)) {
        val currentOpen = openTarget
        if (currentOpen != null) {
            // عرض كشف محصل: تعديل + تصدير + حفظ يصل للجميع
            val entry: Triple<String, List<PaymentRecord>, List<Period>>? = data.getOrNull(branchKeys.indexOf(currentOpen.branchKey))
            val list: List<PaymentRecord> = if (currentOpen.isCurrent)
                entry?.second ?: emptyList<PaymentRecord>()
            else
                entry?.third?.getOrNull(currentOpen.idx)?.payments ?: emptyList<PaymentRecord>()
            AdminStatementScreen(
                vm = vm,
                branchKey = currentOpen.branchKey,
                title = "${currentOpen.branchName} - ${if (currentOpen.isCurrent) "الكشف الحالي" else entry?.third?.getOrNull(currentOpen.idx)?.name ?: "كشف"}",
                payments = list,
                onBack = { openTarget = null },
                onSave = { saved ->
                    val bi = branchKeys.indexOf(currentOpen.branchKey)
                    if (bi >= 0) {
                        val cur: List<PaymentRecord> = entry?.second ?: emptyList<PaymentRecord>()
                        val pers: List<Period> = entry?.third ?: emptyList<Period>()
                        val newCur: List<PaymentRecord> = if (currentOpen.isCurrent) saved else cur
                        val persMut: MutableList<Period> = pers.toMutableList()
                        if (!currentOpen.isCurrent && currentOpen.idx in persMut.indices) {
                            persMut[currentOpen.idx].payments.clear()
                            persMut[currentOpen.idx].payments.addAll(saved)
                        }
                        val newEntry: Triple<String, List<PaymentRecord>, List<Period>> = Triple(currentOpen.branchName, newCur, persMut)
                        data = data.toMutableList().also { it[bi] = newEntry }
                        vm.pushArchiveFor(currentOpen.branchKey, newCur, persMut)
                        vm.toast("✅ تم حفظ التعديلات على كشف ${currentOpen.branchName}")
                    }
                }
            )
            return@Column
        }

        TopBar(vm, "كشوفات المحصلين", onRefresh = { load() }, onSettings = onSettings)
        when {
            loading && !loaded -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            data.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (loaded) "لا توجد كشوفات مرفوعة في السحابة" else "لا توجد فروع",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(data.size, key = { i -> data[i].first }) { i ->
                        val (name, current, periods) = data[i]
                        val bk = branchKeys.getOrElse(i) { "" }
                        var expanded by remember { mutableStateOf(false) }
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Store, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                        Text(
                                            "${current.size + periods.sumOf { it.payments.size }} دفعة | ${formatNum(current.sumOf { it.amount } + periods.sumOf { p -> p.payments.sumOf { it.amount } })} د.ع",
                                            fontSize = 11.5.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Icon(
                                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                                if (expanded) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    Column(
                                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text("📄 الكشف الحالي (${current.size} دفعة)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                if (current.isNotEmpty()) {
                                                    Text("الإجمالي: ${formatNum(current.sumOf { it.amount })} د.ع", fontSize = 12.sp, color = Color.Gray)
                                                }
                                            }
                                            if (current.isNotEmpty()) {
                                                Text(
                                                    "فتح وتعديل ✏️",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Green,
                                                    modifier = Modifier.clickable { openTarget = AdminOpen(bk, name, true, -1) }.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (periods.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("🗂️ الكشوفات المغلقة", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            periods.sortedByDescending { it.createdAt }.forEach { p ->
                                                val pIdx = periods.indexOf(p)
                                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text("📁 ${p.name}", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(
                                                            if (p.closedAt > 0) "${p.createdAt} → ${fmtClosed(p.closedAt)}" else "من ${p.createdAt}",
                                                            fontSize = 10.5.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    Text(
                                                        "${p.payments.size} دفعة | ${formatNum(p.payments.sumOf { it.amount })} د.ع",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray,
                                                        textAlign = TextAlign.End
                                                    )
                                                    Text(
                                                        "فتح ✏️",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Green,
                                                        modifier = Modifier.clickable { openTarget = AdminOpen(bk, name, false, pIdx) }.padding(start = 10.dp, end = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}