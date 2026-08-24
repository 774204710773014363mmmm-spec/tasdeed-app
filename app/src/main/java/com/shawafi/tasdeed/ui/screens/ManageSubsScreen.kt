package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.data.Subscriber
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues
) {
    var query by remember { mutableStateOf("") }
    val subs by vm.allSubscribers.collectAsState()
    val devMode by vm.devMode.collectAsState()
    val paidTotals by vm.paidTotals.collectAsState()

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "إدارة المشتركين", onRefresh = { vm.reloadSubscribers() })

        if (!devMode) {
            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                Text("⚠️ هذه الصفحة مخصصة لوضع المطور", color = Color.Gray)
            }
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("ابحث باسم المشترك / رقم العداد...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val filtered = remember(query, subs) {
            subs.filter {
                query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.meterNumber.contains(query, true) ||
                it.subscriberNumber.contains(query, true)
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Text("لا يوجد مشتركين", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.key }) { sub ->
                    ManageSubCard(
                        sub = sub,
                        paidTotal = paidTotals[sub.name.trim().lowercase()] ?: 0.0,
                        onToggleHidden = { vm.setSubscriberHidden(sub.key, it) },
                        onToggleAmounts = { vm.setSubscriberHideAmounts(sub.key, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ManageSubCard(
    sub: Subscriber,
    paidTotal: Double,
    onToggleHidden: (Boolean) -> Unit,
    onToggleAmounts: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sub.hidden || sub.hideAmounts)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GreenLight, Green))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sub.name.take(2).ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("📟 ${sub.meterNumber.ifEmpty { "-" }}", fontSize = 12.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "💰 ${formatNum(sub.displayBalance)} د.ع",
                        fontSize = 12.sp,
                        color = if (sub.displayBalance > 0) Color(0xFFDC2626) else Green
                    )
                    if (paidTotal > 0) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "✅ مسدد: ${formatNum(paidTotal)} د.ع",
                            fontSize = 11.sp,
                            color = Green,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🙈 إخفاء عن الفروع", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(
                        if (sub.hidden) "مخفي — يظهر فقط لجهاز المطور" else "ظاهر لكل الفروع",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(checked = sub.hidden, onCheckedChange = onToggleHidden)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔒 إخفاء المبالغ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(
                        if (sub.hideAmounts) "المبالغ مخفية عن كل الفروع" else "المبالغ ظاهرة للجميع",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(checked = sub.hideAmounts, onCheckedChange = onToggleAmounts)
            }
        }
    }
}
