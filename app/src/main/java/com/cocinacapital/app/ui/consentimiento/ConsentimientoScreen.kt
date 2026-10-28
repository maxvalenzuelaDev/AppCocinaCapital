package com.cocinacapital.app.ui.consentimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R

/**
 * Muestra [contenido] solo cuando la cuenta ya respondió el consentimiento (HU-03); antes, la
 * pantalla que lo pide. Mientras se leen las preferencias se ve el fondo, sin parpadeos.
 */
@Composable
fun ConConsentimiento(
    usuarioId: String,
    viewModel: ConsentimientoViewModel = viewModel(
        key = "consentimiento_$usuarioId",
        factory = ConsentimientoViewModel.factory(usuarioId)
    ),
    contenido: @Composable () -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    when (estado) {
        EstadoConsentimiento.CARGANDO -> Surface(Modifier.fillMaxSize()) {}
        EstadoConsentimiento.PENDIENTE -> ConsentimientoScreen(viewModel::onAceptar, viewModel::onRechazar)
        EstadoConsentimiento.RESPONDIDO -> contenido()
    }
}

@Composable
fun ConsentimientoScreen(onAceptar: () -> Unit, onRechazar: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.PrivacyTip,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    stringResource(R.string.consentimiento_titulo),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() }
                )
                Text(stringResource(R.string.consentimiento_intro), style = MaterialTheme.typography.bodyLarge)

                Subtitulo(stringResource(R.string.consentimiento_que_titulo))
                Punto(Icons.Outlined.History, stringResource(R.string.consentimiento_que_actividad))
                Punto(Icons.Outlined.Restaurant, stringResource(R.string.consentimiento_que_preferencias))

                Subtitulo(stringResource(R.string.consentimiento_garantias_titulo))
                Punto(Icons.Outlined.LocationOn, stringResource(R.string.consentimiento_garantia_ubicacion))
                Punto(Icons.Outlined.Block, stringResource(R.string.consentimiento_garantia_terceros))
                Punto(Icons.Outlined.ToggleOn, stringResource(R.string.consentimiento_garantia_revocar))
            }
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAceptar, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.consentimiento_aceptar))
                }
                // Rechazar es igual de visible que aceptar: el consentimiento debe ser libre.
                OutlinedButton(onClick = onRechazar, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.consentimiento_rechazar))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.consentimiento_rechazar_ayuda),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun Punto(icono: ImageVector, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
