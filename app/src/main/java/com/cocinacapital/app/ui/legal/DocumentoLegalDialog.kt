package com.cocinacapital.app.ui.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cocinacapital.app.R

/** Textos legales de la app (T-16): están en res/values/legal.xml. */
enum class DocumentoLegal(@StringRes val tituloRes: Int, val secciones: List<Pair<Int, Int>>) {
    PRIVACIDAD(
        R.string.legal_privacidad,
        listOf(
            R.string.privacidad_1_titulo to R.string.privacidad_1_texto,
            R.string.privacidad_2_titulo to R.string.privacidad_2_texto,
            R.string.privacidad_3_titulo to R.string.privacidad_3_texto,
            R.string.privacidad_4_titulo to R.string.privacidad_4_texto,
            R.string.privacidad_5_titulo to R.string.privacidad_5_texto,
            R.string.privacidad_6_titulo to R.string.privacidad_6_texto,
            R.string.privacidad_7_titulo to R.string.privacidad_7_texto
        )
    ),
    TERMINOS(
        R.string.legal_terminos,
        listOf(
            R.string.terminos_1_titulo to R.string.terminos_1_texto,
            R.string.terminos_2_titulo to R.string.terminos_2_texto,
            R.string.terminos_3_titulo to R.string.terminos_3_texto,
            R.string.terminos_4_titulo to R.string.terminos_4_texto,
            R.string.terminos_5_titulo to R.string.terminos_5_texto
        )
    )
}

/** Muestra el documento a pantalla completa; se cierra con la X o con el botón atrás. */
@Composable
fun DocumentoLegalDialog(documento: DocumentoLegal, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(documento.tituloRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.legal_cerrar))
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.legal_aviso_academico),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    documento.secciones.forEach { (titulo, texto) ->
                        Text(
                            stringResource(titulo),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(stringResource(texto), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.legal_vigencia),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Enlaces para abrir los documentos; pasan a otra línea si no caben, sin cortar las palabras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnlacesLegales(onAbrir: (DocumentoLegal) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentoLegal.entries.forEach { documento ->
            TextButton(onClick = { onAbrir(documento) }) {
                Text(stringResource(documento.tituloRes), maxLines = 1)
            }
        }
    }
}
