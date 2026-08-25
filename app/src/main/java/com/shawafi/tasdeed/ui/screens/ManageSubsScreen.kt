package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.window.Dialog
import com.shawafi.tasdeed.data.Subscriber
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenLight
import com.shawafi.tasdeed.ui.theme.ElectricCardBrush

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var deleteTarget by remember { mutableStateOf<Subscriber?>(null) }

    Column(modifier = modifier.padding(padding)) {
        TopBar(vm, "إدارة المشتركين", onRefresh = { vm.reloadSubscribers(); vm.fetchPaidTotals() })

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
                        paidTotal = paidTotals[sub.name.lowercase()] ?: 0.0,
                        onToggleHidden = { vm.setSubscriberHidden(sub.key, it) },
                        onToggleAmounts = { vm.setSubscriberHideAmounts(sub.key, it) },
                        onLongPress = { deleteTarget = sub }
                    )
                }
            }
        }
    }

    // تأكيد حذف مشترك
    val delSub = deleteTarget
    if (delSub != null) {
        Dialog(onDismissRequest = { deleteTarget = null }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("🗑 حذف مشترك", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("هل تريد حذف «${delSub.name}» من النظام بالكامل؟", fontSize = 13.5.sp)
                    Text("سيتم حذفه من جهازك ومن السحابة ولا سيظهر لأي فرع.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { deleteTarget = null }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("إلغاء") }
                        Button(
                            onClick = { vm.deleteSubscriber(delSub.key); deleteTarget = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) { Text("🗑 حذف", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageSubCard(
    sub: Subscriber,
    paidTotal: Double = 0.0,
    onToggleHidden: (Boolean) -> Unit,
    onToggleAmounts: (Boolean) -> Unit,
    onLongPress: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = onLongPress
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // هيدر متدرج: الاسم والعداد والمبالغ
            Row(
                Modifier.fillMaxWidth().background(ElectricCardBrush).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GreenLight, Green))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sub.name.take(2).ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF0C4A6E))
                    Spacer(Modifier.height(2.dp))
                    Text("📟 ${sub.meterNumber.ifEmpty { "-" }}", fontSize = 12.sp, color = Color(0xFF0369A1))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "💰 ${formatNum(sub.displayBalance)} د.ع",
                        fontSize = 12.sp,
                        color = if (sub.displayBalance > 0) Color(0xFFDC2626) else Green,
                        fontWeight = FontWeight.Medium
                    )
                    if (paidTotal > 0) {
                        Text("✅ مسدد ${formatNum(paidTotal)} د.ع", fontSize = 11.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Medium)
                    }
                }
            }
            // محتوى البطاقة: مفاتيح الإخفاء + تلميح الحذف
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🙈 إخفاء عن الفروع", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(if (sub.hidden) "مخفي — يظهر فقط لجهاز المطور" else "ظاهر لكل الفروع", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = sub.hidden, onCheckedChange = onToggleHidden)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🔒 إخفاء المبالغ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(if (sub.hideAmounts) "المبالغ مخفية عن كل الفروع" else "المبالغ ظاهرة للجميع", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = sub.hideAmounts, onCheckedChange = onToggleAmounts)
                }
                Text("💡 اضغط مطولاً على البطاقة لحذف المشترك", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
