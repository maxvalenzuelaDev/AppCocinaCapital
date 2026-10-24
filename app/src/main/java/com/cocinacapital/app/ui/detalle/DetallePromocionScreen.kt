package com.cocinacapital.app.ui.detalle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R
import com.cocinacapital.app.ui.detalle.components.DetalleTopBar

/**
 * Detalle de una promoción (RF-09). Por ahora solo muestra el id recibido por la ruta;
 * HU-12 agrega el ViewModel con condiciones, vigencia, horario y cupos.
 */
@Composable
fun DetallePromocionScreen(
    promocionId: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            DetalleTopBar(
                titulo = stringResource(R.string.detalle_promocion_titulo),
                onVolver = onVolver
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.detalle_promocion_pendiente, promocionId),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
