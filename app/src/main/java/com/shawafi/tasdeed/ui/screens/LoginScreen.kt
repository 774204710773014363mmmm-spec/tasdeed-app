package com.shawafi.tasdeed.ui.screens

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.shawafi.tasdeed.ui.AppViewModel
import com.shawafi.tasdeed.ui.theme.Green
import com.shawafi.tasdeed.ui.theme.GreenBrush
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    val bioEnabled by vm.bioEnabled.collectAsState()
    var bioAutoShown by rememberSaveable { mutableStateOf(false) }

    // البصمة مفعلة + بيانات محفوظة: افتح نافذة البصمة تلقائياً فور دخول الشاشة
    LaunchedEffect(bioEnabled, vm.hasSavedCredentials()) {
        if (bioEnabled && vm.hasSavedCredentials() && !bioAutoShown) {
            bioAutoShown = true
            delay(400)
            launchFingerprint()
        }
    }

    fun launchFingerprint() {
        val activity = context as? FragmentActivity ?: return
        try {
            when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(context),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                vm.loginWithSavedCredentials()
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                                    vm.toast("❌ ${errString}", true)
                                }
                            }
                        }
                    )
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("تسجيل الدخول بالبصمة")
                            .setSubtitle("ضع بصمتك للدخول")
                            .setNegativeButtonText("إلغاء")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            .build()
                    )
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    vm.toast("⚠️ لا توجد بصمة مسجلة في الجوال", true)
                else ->
                    vm.toast("❌ الجوال لا يدعم البصمة", true)
            }
        } catch (e: Exception) {
            vm.toast("❌ تعذر فتح البصمة: ${e.message ?: "خطأ غير معروف"}", true)
        }
    }

    Box(
        modifier = modifier
            .padding(padding)
            .background(Brush.verticalGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF38BDF8))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.12f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 38.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("تسديد الشوافي", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                "محطة كهرباء الحطباني",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(14.dp))
            OnlinePill(vm.isOnline.collectAsState().value)
            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("اسم المستخدم") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(14.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GreenBrush),
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("دخول", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                error = "أدخل اسم المستخدم وكلمة المرور"
                                return@OutlinedButton
                            }
                            error = ""
                            vm.login(username, password, rememberMe, useNetwork = false)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !loading
                    ) {
                        Text("دخول بدون إنترنت", fontSize = 14.sp)
                    }

                    if (bioEnabled && vm.hasSavedCredentials()) {
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = { launchFingerprint() },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Green)
                            Spacer(Modifier.width(8.dp))
                            Text("دخول بالبصمة", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}