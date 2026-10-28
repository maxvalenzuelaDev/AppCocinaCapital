package com.cocinacapital.app.ui.billetera

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Botón para guardar una promoción en la billetera.
 *
 * Usa el BilleteraViewModel, que se conecta
 * con la tabla beneficios_usuario en Supabase.
 */
@Composable
fun GuardarPromocionButton(
    promocionId: String,
    viewModel: BilleteraViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Actualiza las promociones guardadas cada vez
    // que el consumidor vuelve al detalle.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.cargarBeneficios()
    }

    val yaGuardada = state.beneficios.any {
        it.promocionId == promocionId
    }

    if (yaGuardada) {
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.BookmarkAdded,
                contentDescription = null
            )

            Text("  Guardada en mi billetera")
        }
    } else {
        Button(
            onClick = {
                viewModel.guardarBeneficio(promocionId)
            },
            enabled = !state.cargando && !state.procesando,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.cargando || state.procesando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.BookmarkAdd,
                    contentDescription = null
                )

                Text("  Guardar en mi billetera")
            }
        }
    }
}
