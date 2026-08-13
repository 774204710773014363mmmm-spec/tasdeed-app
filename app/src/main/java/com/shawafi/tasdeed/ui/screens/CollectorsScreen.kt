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

@Composable
fun CollectorsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    onSettings: () -> Unit = {}
) {
    var data by remember { mutableStateOf<List<Triple<String, List<PaymentRecord>, List<Period>>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                vm.repo.getBranches().entries.sortedBy { it.value.name }.mapNotNull { (k, b) ->
                    try {
                        vm.repo.fetchArchive(k)?.let { Triple(b.name, it.first, it.second) }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            data = res
            loading = false
            loaded = true
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.padding(padding)) {
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
                                        Text("📄 الكشف الحالي (${current.size} دفعة)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        if (current.isNotEmpty()) {
                                            Text("الإجمالي: ${formatNum(current.sumOf { it.amount })} د.ع", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        if (periods.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("🗂️ الكشوفات المغلقة", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            periods.sortedByDescending { it.createdAt }.forEach { p ->
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
