package ir.namyranservice.customer.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NamyranColors {
    val blue = Color(0xFF0757F9)
    val cyan = Color(0xFF04C8D9)
    val green = Color(0xFF06BFA5)
    val navy = Color(0xFF102D59)
    val lightBlue = Color(0xFFE9F5FF)
    val background = Color(0xFFF6FAFF)
    val secondary = Color(0xFF64748B)
    val night = Color(0xFF081525)
    val nightSurface = Color(0xFF10243B)
}

@Composable
fun NamyranTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (dark) darkColorScheme(
        primary = Color(0xFF91BCFF), secondary = NamyranColors.cyan,
        tertiary = NamyranColors.green, background = NamyranColors.night,
        surface = NamyranColors.nightSurface, onSurface = Color(0xFFF2F7FF)
    ) else lightColorScheme(
        primary = NamyranColors.blue, secondary = NamyranColors.cyan,
        tertiary = NamyranColors.green, background = NamyranColors.background,
        surface = Color.White, onSurface = NamyranColors.navy
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
