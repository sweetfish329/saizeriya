package com.example.saizeriya.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// サイゼリヤのブランドカラーに近い配色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),       // 緑（サイゼリヤのイメージ）
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFD32F2F),     // 赤（イタリアンカラー）
    background = Color(0xFFFFFDE7),    // クリーム
    surface = Color.White,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B)
)

@Composable
fun SaizeriyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
