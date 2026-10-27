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

// Paleta de marca: terracota (primary), café (secondary) y dorado (tertiary). Se definen todos los roles
// para que Material no complete con su morado por defecto (avatar, chips, insignias, indicador de la
// barra inferior). Cada texto cumple WCAG AA (4,5:1) sobre su fondo en ambos temas.
private val LightColors = lightColorScheme(
    primary = Color(0xFFB5451B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    inversePrimary = Color(0xFFFFB59B),
    secondary = Color(0xFF77574B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160D),
    // Dorado oscurecido (T-18): #C79200 sobre fondo claro daba 2,65:1; este da 5,06:1.
    tertiary = Color(0xFF8A6500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9C),
    onTertiaryContainer = Color(0xFF2B1F00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceTint = Color(0xFFB5451B),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF6E5DF),
    surfaceContainerHighest = Color(0xFFF1DFD9)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5C1A00),
    primaryContainer = Color(0xFF822E0A),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFFB5451B),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFF2C14E),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDF9C),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceTint = Color(0xFFFFB59B),
    inverseSurface = Color(0xFFF1DFD9),
    inverseOnSurface = Color(0xFF392E2B),
    outline = Color(0xFFA08D87),
    outlineVariant = Color(0xFF53433E),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D322F)
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
