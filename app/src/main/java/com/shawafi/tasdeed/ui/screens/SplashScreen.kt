package com.shawafi.tasdeed.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(if (visible) 1f else 0.6f, tween(650), label = "logoScale")
    val logoAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "logoAlpha")
    val textAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(1000), label = "textAlpha")
    val boxAlpha by animateFloatAsState(if (leaving) 0f else 1f, tween(450), label = "boxAlpha")

    LaunchedEffect(Unit) {
        visible = true
        delay(1800)
        leaving = true
        delay(450)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(boxAlpha)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0284C7), Color(0xFF0369A1)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.12f)))),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 46.sp)
            }
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.alpha(textAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("تسديد", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "محطة كهرباء الشوافي",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}