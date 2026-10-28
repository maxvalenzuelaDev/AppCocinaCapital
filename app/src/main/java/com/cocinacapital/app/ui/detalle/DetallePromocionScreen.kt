
package com.cocinacapital.app.ui.detalle

import androidx.compose.foundation.layout.*
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
import com.cocinacapital.app.ui.billetera.GuardarPromocionButton

/**
 * Detalle de una promoción (RF-09).
 *
 * Mantiene la pantalla provisional de promociones
 * e incorpora la opción de guardar en la billetera.
 *
 * HU-12 agregará las condiciones, vigencia,
 * horarios y cupos de la promoción.
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
                titulo = stringResource(
                    R.string.detalle_promocion_titulo
                ),
                onVolver = onVolver
            )
        },

        // Botón de guardar en la parte inferior.
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    )
            ) {
                GuardarPromocionButton(
                    promocionId = promocionId
                )
            }
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
                text = stringResource(
                    R.string.detalle_promocion_pendiente,
                    promocionId
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}
