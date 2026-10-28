package com.cocinacapital.app.ui.billetera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.CodigoBeneficio
import com.cocinacapital.app.util.aPixeles
import com.cocinacapital.app.util.generarQr

/**
 * Código del beneficio para mostrar en el local (HU-14): QR y texto, por si el local lo escribe a
 * mano. Mientras está abierto sube el brillo de la pantalla para que el QR se lea mejor.
 */
@Composable
fun CodigoBeneficioDialog(
    titulo: String,
    localNombre: String?,
    codigo: String,
    recienObtenido: Boolean = false,
    onCerrar: () -> Unit
) {
    BrilloMaximo()
    val legible = CodigoBeneficio.formatear(codigo)
    Dialog(onDismissRequest = onCerrar) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recienObtenido) {
                    Text(
                        stringResource(R.string.beneficio_obtenido_titulo),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.beneficio_obtenido_texto),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    stringResource(R.string.billetera_codigo_titulo),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!localNombre.isNullOrBlank()) {
                    Text(localNombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CodigoQr(codigo, legible, Modifier.size(240.dp))
                Text(
                    legible,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        // Letra por letra para lectores de pantalla.
                        contentDescription = codigo.toList().joinToString(" ")
                    }
                )
                Text(
                    stringResource(R.string.billetera_codigo_ayuda),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.billetera_cerrar))
                }
            }
        }
    }
}

/** QR negro sobre blanco también en modo oscuro: invertido, muchos lectores no lo reconocen. */
@Composable
private fun CodigoQr(codigo: String, legible: String, modifier: Modifier = Modifier) {
    val imagen = remember(codigo) {
        val matriz = generarQr(CodigoBeneficio.contenidoQr(codigo), 512)
        Bitmap.createBitmap(matriz.aPixeles(), matriz.width, matriz.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(
        bitmap = imagen,
        contentDescription = stringResource(R.string.billetera_codigo_qr, legible),
        filterQuality = FilterQuality.None,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
    )
}

@Composable
private fun BrilloMaximo() {
    val activity = LocalContext.current.buscarActivity() ?: return
    DisposableEffect(activity) {
        val ventana = activity.window
        val anterior = ventana.attributes.screenBrightness
        ventana.attributes = ventana.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { ventana.attributes = ventana.attributes.apply { screenBrightness = anterior } }
    }
}

private tailrec fun Context.buscarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.buscarActivity()
    else -> null
}
