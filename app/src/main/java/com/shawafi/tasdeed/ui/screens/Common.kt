package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.Amber

data class NavItem(val id: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavBar(vm: AppViewModel, current: String, onNav: (String) -> Unit) {
    val pendingCount = vm.pendingPayments.value.size + vm.pendingFreePayments.value.size
    NavigationBar {
        val items = listOf(
            NavItem("home", "الفواتير", Icons.Filled.ReceiptLong),
            NavItem("free", "حساباتي", Icons.Filled.AccountBalanceWallet),
            NavItem("archive", "الكشوفات", Icons.Filled.Archive),
            NavItem("settings", "الإعدادات", Icons.Filled.Settings)
        )
        items.forEach { item ->
            NavigationBarItem(
                selected = current == item.id,
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
                label = { Text(item.label, fontSize = 11.sp) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(vm: AppViewModel, title: String, onRefresh: (() -> Unit)? = null) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                if (vm.branchName.value.isNotEmpty()) {
                    Text(vm.branchName.value, fontSize = 11.sp, color = Color.Gray)
                }
            }
        },
        actions = {
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Green,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}
