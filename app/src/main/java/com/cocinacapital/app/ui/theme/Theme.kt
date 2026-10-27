package com.cocinacapital.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cocinacapital.app.data.model.TemaApp

val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

// Paleta base (terracota + dorado). Si el equipo ya tiene un tema, usen el suyo y borren este archivo.
private val LightColors = lightColorScheme(
    primary = Color(0xFFB5451B),
    secondary = Color(0xFF77574B),
    // Dorado oscurecido (T-18): #C79200 sobre fondo claro daba 2,65:1; este da 5,06:1 (WCAG AA).
    tertiary = Color(0xFF8A6500)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    secondary = Color(0xFFE7BDB0),
    tertiary = Color(0xFFF2C14E)
)

/**
 * Verde para mensajes de éxito con contraste suficiente en ambos temas (T-18):
 * 6,2:1 sobre el fondo claro y 9,5:1 sobre el oscuro.
 */
val ColorScheme.exito: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF81C995) else Color(0xFF1E6B3A)

@Composable
fun CocinaCapitalTheme(
    temaApp: TemaApp = TemaApp.SISTEMA,
    darkTheme: Boolean = when (temaApp) {
        TemaApp.CLARO -> false
        TemaApp.OSCURO -> true
        TemaApp.SISTEMA -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false, // true = colores del fondo de pantalla (Android 12+)
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
