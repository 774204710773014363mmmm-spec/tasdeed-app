package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenBrush
import com.shawafi.tasdeed.ui.theme.Amber

data class NavItem(val id: String, val label: String, val icon: ImageVector)

@Composable
fun BottomNavBar(vm: AppViewModel, current: String, onNav: (String) -> Unit) {
    val pendingCount = vm.pendingPayments.value.size + vm.pendingFreePayments.value.size
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            NavItem("home", "الفواتير", Icons.Filled.ReceiptLong),
            NavItem("free", "حساباتي", Icons.Filled.AccountBalanceWallet),
            NavItem("archive", "الكشوفات", Icons.Filled.Archive),
            NavItem("settings", "الإعدادات", Icons.Filled.Settings)
        )
        items.forEach { item ->
            val selected = current == item.id
            NavigationBarItem(
                selected = selected,
                onClick = { onNav(item.id) },
                icon = {
                    if (item.id == "free" && pendingCount > 0) {
                        BadgedBox(badge = { Badge { Text(pendingCount.toString(), fontSize = 10.sp) } }) {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(vm: AppViewModel, title: String, onRefresh: (() -> Unit)? = null) {
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
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = Color.White) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}
