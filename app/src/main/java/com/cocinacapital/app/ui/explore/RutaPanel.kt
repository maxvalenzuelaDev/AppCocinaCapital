package com.cocinacapital.app.ui.explore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.ModoRuta
import java.util.Locale
import kotlin.math.ceil

@Composable internal fun RutaPanel(state: RutaUiState, calcular: () -> Unit, usarGps: () -> Unit,
    modo: (ModoRuta) -> Unit, navegar: () -> Unit, detalle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.modo == ModoRuta.A_PIE, { modo(ModoRuta.A_PIE) }, label = { Text(stringResource(R.string.ruta_a_pie)) })
            FilterChip(state.modo == ModoRuta.AUTO, { modo(ModoRuta.AUTO) }, label = { Text(stringResource(R.string.ruta_auto)) })
        }
        when (state) {
            is RutaUiState.SinUbicacion -> {
                Text(stringResource(R.string.ruta_necesita_gps), style = MaterialTheme.typography.bodySmall)
                Button(onClick = usarGps) { Text(stringResource(R.string.gps_obtener)) }
            }
            is RutaUiState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.ruta_calculando))
            }
            is RutaUiState.Success -> state.ruta.let { route ->
                val locale = Locale.forLanguageTag("es-CL")
                val distancia = if (route.metros < 1000) stringResource(R.string.ruta_metros, ceil(route.metros).toInt())
                    else stringResource(R.string.ruta_km, String.format(locale, "%.1f", route.metros / 1000))
                Text(stringResource(R.string.ruta_resumen, distancia, ceil(route.segundos / 60).toInt().coerceAtLeast(1)),
                    style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = navegar, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ruta_navegar)) }
                    OutlinedButton(onClick = detalle, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ruta_detalle)) }
                }
            }
            is RutaUiState.Error -> {
                Text(stringResource(if (state.sinRuta) R.string.ruta_no_encontrada else R.string.ruta_error),
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = calcular) { Text(stringResource(R.string.ruta_reintentar)) }
            }
            is RutaUiState.Pendiente -> Button(onClick = calcular, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ruta_ver)) }
            is RutaUiState.SinLocal -> Unit
        }
    }
}
