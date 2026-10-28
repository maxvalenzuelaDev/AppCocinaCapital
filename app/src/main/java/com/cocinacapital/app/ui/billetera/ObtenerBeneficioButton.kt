package com.cocinacapital.app.ui.billetera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.EstadoBeneficio

/**
 * Botón del detalle de una promoción para obtener su beneficio (HU-13). Si ya lo tiene, muestra su
 * código; si lo canjeó, lo indica. [obtenible] es `false` cuando la promoción terminó, no tiene
 * cupos o está pausada: Supabase igual lo valida al obtenerlo.
 */
@Composable
fun ObtenerBeneficioButton(
    promocionId: String,
    titulo: String,
    localNombre: String?,
    obtenible: Boolean,
    onCuposCambiaron: () -> Unit,
    viewModel: BeneficioPromocionViewModel = viewModel(
        key = "beneficio_$promocionId",
        factory = BeneficioPromocionViewModel.factory(promocionId)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.revisar() }
    LaunchedEffect(viewModel) { viewModel.obtenidos.collect { onCuposCambiaron() } }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val beneficio = state.beneficio
        when {
            beneficio?.estado == EstadoBeneficio.CANJEADO -> OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.TaskAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.beneficio_ya_canjeado))
            }
            beneficio != null -> OutlinedButton(onClick = viewModel::onVerCodigo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.beneficio_ver_codigo))
            }
            else -> Button(
                onClick = viewModel::onObtener,
                enabled = obtenible && !state.cargando && !state.obteniendo,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.cargando || state.obteniendo) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (obtenible) R.string.beneficio_obtener else R.string.beneficio_no_obtenible))
                }
            }
        }
        state.error?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val obtenido = state.beneficio
    if (state.mostrarCodigo && obtenido != null) {
        CodigoBeneficioDialog(
            titulo = titulo,
            localNombre = localNombre,
            codigo = obtenido.codigo,
            recienObtenido = state.recienObtenido,
            onCerrar = viewModel::onCerrarCodigo
        )
    }
}
