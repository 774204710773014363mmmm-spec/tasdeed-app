package com.shawafi.smsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shawafi.smsapp.ui.screens.SmsMainScreen
import com.shawafi.smsapp.ui.theme.SmsAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsAppTheme {
                SmsApp()
            }
        }
    }
}

@Composable
private fun SmsApp() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel()
    var unlocked by remember { mutableStateOf(false) }

    // استقبال نتائج الإرسال من النظام
    val receiver = remember(context) { SmsSender.register(context.applicationContext) { rowId, success -> vm.smsResult(rowId, success) } }
    DisposableEffect(Unit) {
        onDispose {
            SmsSender.unregister(context.applicationContext, receiver)
        }
    }

    if (unlocked) {
        SmsMainScreen(vm)
    } else {
        PasswordGate(onUnlock = { unlocked = true })
    }
}

@Composable
private fun PasswordGate(onUnlock: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    val correct = "773520853"

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("تطبيق الرسائل", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("محطة كهرباء الشوافي", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("كلمة المرور") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Button(
                onClick = {
                    if (pass == correct) onUnlock() else pass = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("دخول")
            }
            Text(
                "إدارة فواتير الكهرباء وإرسال الرسائل النصية للمشتركين",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}