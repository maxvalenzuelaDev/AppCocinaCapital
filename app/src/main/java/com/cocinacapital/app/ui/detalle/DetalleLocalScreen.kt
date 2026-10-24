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
 * Detalle de un local (RF-05). Por ahora solo muestra el id recibido por la ruta;
 * HU-07 agrega el ViewModel y la información del local.
 */
@Composable
fun DetalleLocalScreen(
    localId: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            DetalleTopBar(
                titulo = stringResource(R.string.detalle_local_titulo),
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
                text = stringResource(R.string.detalle_local_pendiente, localId),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
