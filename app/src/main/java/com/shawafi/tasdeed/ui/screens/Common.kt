package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenBrush
import com.shawafi.tasdeed.ui.theme.Amber

data class NavItem(val id: String, val label: String, val icon: ImageVector, val iconOutline: ImageVector)

@Composable
fun BottomNavBar(vm: AppViewModel, current: String, onNav: (String) -> Unit) {
    val pendingCount = vm.pendingPayments.value.size + vm.pendingFreePayments.value.size
    val devMode by vm.devMode.collectAsState()
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val items = buildList {
            add(NavItem("home", "الفواتير", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong))
            if (devMode) add(NavItem("collectors", "كشوفات المحصلين", Icons.Filled.People, Icons.Outlined.People))
            add(NavItem("archive", "الكشوفات", Icons.Filled.Archive, Icons.Outlined.Archive))
            add(NavItem("free", "حساباتي", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet))
        }
        items.forEach { item ->
            val selected = current == item.id
            NavigationBarItem(
                selected = selected,
                onClick = { onNav(item.id) },
                icon = {
                    if (item.id == "free" && pendingCount > 0) {
                        BadgedBox(badge = { Badge { Text(pendingCount.toString(), fontSize = 10.sp) } }) {
                            Icon(if (selected) item.icon else item.iconOutline, contentDescription = item.label)
                        }
                    } else {
                        Icon(if (selected) item.icon else item.iconOutline, contentDescription = item.label)
                    }
                },
                label = {
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green,
                    selectedTextColor = Green,
                    indicatorColor = Green.copy(alpha = 0.16f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}

@Composable
fun OnlinePill(online: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (online) Color(0xFF22C55E).copy(alpha = 0.22f) else Color(0xFFEF4444).copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (online) Color(0xFF22C55E) else Color(0xFFEF4444)))
        Spacer(Modifier.width(6.dp))
        Text(
            if (online) "متصل" else "غير متصل",
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(vm: AppViewModel, title: String, onRefresh: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val online by vm.isOnline.collectAsState()
    TopAppBar(
        modifier = Modifier.background(GreenBrush),
        title = {
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (vm.branchName.value.isNotEmpty()) {
                    Text(vm.branchName.value, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        },
        actions = {
            OnlinePill(online)
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = Color.White) }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات", tint = Color.White) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) Green else MaterialTheme.colorScheme.surface,
        tonalElevation = if (highlight) 0.dp else 1.dp,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (highlight) androidx.compose.ui.graphics.Color.White else Green,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (highlight) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}
