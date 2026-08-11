package com.shawafi.tasdeed.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    padding: PaddingValues
) {
    var username by rememberSaveable { mutableStateOf(vm.repo.store.getString("saved_user") ?: "") }
    var password by rememberSaveable { mutableStateOf(vm.repo.store.getString("saved_pass") ?: "") }
    var rememberMe by rememberSaveable { mutableStateOf(vm.repo.store.getString("remember_me") == "true") }
    var error by rememberSaveable { mutableStateOf("") }
    val loading by vm.loading.collectAsState()

    Column(
        modifier = modifier
            .padding(padding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))
        Surface(
            modifier = Modifier.size(88.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = Green,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("تس", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("تسديد الحطباني", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "تطبيق تسديد فواتير الكهرباء - محطة كهرباء الشوافي",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("اسم المستخدم") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Text("تذكرني", fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    error = "أدخل اسم المستخدم وكلمة المرور"
                    return@Button
                }
                error = ""
                vm.login(username, password, rememberMe, useNetwork = true)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("دخول", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    error = "أدخل اسم المستخدم وكلمة المرور"
                    return@OutlinedButton
                }
                error = ""
                vm.login(username, password, rememberMe, useNetwork = false)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("دخول بدون إنترنت", fontSize = 14.sp)
        }
        Spacer(Modifier.height(30.dp))
    }
}
