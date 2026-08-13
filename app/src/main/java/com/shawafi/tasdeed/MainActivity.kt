package com.shawafi.tasdeed

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.screens.ArchiveScreen
import com.shawafi.tasdeed.ui.screens.BottomNavBar
import com.shawafi.tasdeed.ui.screens.FreeScreen
import com.shawafi.tasdeed.ui.screens.HomeScreen
import com.shawafi.tasdeed.ui.screens.LoginScreen
import com.shawafi.tasdeed.ui.screens.SettingsScreen
import com.shawafi.tasdeed.ui.screens.SplashScreen
import com.shawafi.tasdeed.ui.screens.StatementScreen
import com.shawafi.tasdeed.ui.theme.TasdeedTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appVm: AppViewModel = viewModel()
            val dark by appVm.darkTheme.collectAsState()
            val fs by appVm.fontScale.collectAsState()
            val density = LocalDensity.current
            TasdeedTheme(dark = dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * (fs / 100f))
                ) {
                    MainApp(appVm)
                }
            }
        }
    }
}

@Composable
fun MainApp(vm: AppViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val loggedIn by vm.isLoggedIn.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsState()
    var screen by remember { mutableStateOf("home") }
    var statement by remember { mutableStateOf<Triple<String, Int, String>?>(null) }

    LaunchedEffect(msg) {
        msg?.let {
            val res = snackbar.showSnackbar(it.text, duration = androidx.compose.material3.SnackbarDuration.Short)
            if (res == SnackbarResult.Dismissed) vm.clearToast()
            delay(200)
            vm.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (loggedIn && statement == null) {
                BottomNavBar(vm, screen) { screen = it }
            }
        }
    ) { padding ->
        if (loggedIn) {
            val stmt = statement
            if (stmt != null) {
                StatementScreen(vm, stmt.first, stmt.second, kind = stmt.third, onBack = { statement = null })
            } else {
                MainNav(vm, screen, padding, onNav = { screen = it }, onOpenStatement = { name, idx, kind -> statement = Triple(name, idx, kind) })
            }
        } else {
            LoginScreen(vm, Modifier.fillMaxSize(), padding)
        }
    }
}

@Composable
fun MainNav(
    vm: AppViewModel,
    screen: String,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onNav: (String) -> Unit,
    onOpenStatement: (String, Int, String) -> Unit = { _, _, _ -> }
) {
    when (screen) {
        "home" -> HomeScreen(vm, Modifier.fillMaxSize(), padding, onNav = onNav, onSettings = { onNav("settings") })
        "free" -> FreeScreen(vm, Modifier.fillMaxSize(), padding, onNav = onNav, onOpenStatement = onOpenStatement, onSettings = { onNav("settings") })
        "archive" -> ArchiveScreen(vm, Modifier.fillMaxSize(), padding, onNav = onNav, onOpenStatement = onOpenStatement, onSettings = { onNav("settings") })
        "settings" -> SettingsScreen(vm, Modifier.fillMaxSize(), padding, onNav = onNav)
        else -> HomeScreen(vm, Modifier.fillMaxSize(), padding, onNav = onNav, onSettings = { onNav("settings") })
    }
}
