package com.cocinacapital.app.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.precioTexto
import com.cocinacapital.app.ui.components.BotonFavorito
import com.cocinacapital.app.ui.detalle.components.DetalleTopBar
import com.cocinacapital.app.ui.home.components.PromocionCard
import com.cocinacapital.app.util.LocaleCL

/** Detalle de un local (RF-05) "con estado": conecta el ViewModel con [DetalleLocalContent]. */
@Composable
fun DetalleLocalScreen(
    localId: String,
    onVolver: () -> Unit,
    onPromocionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetalleLocalViewModel = viewModel(factory = DetalleLocalViewModel.factory(localId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val esFavorito by viewModel.esFavorito.collectAsStateWithLifecycle()

    DetalleLocalContent(
        uiState = uiState,
        esFavorito = esFavorito,
        onVolver = onVolver,
        onReintentar = viewModel::onReintentar,
        onFavoritoClick = viewModel::onFavoritoClick,
        onPromocionClick = onPromocionClick,
        modifier = modifier
    )
}

/** Detalle de un local "sin estado" (stateless). */
@Composable
fun DetalleLocalContent(
    uiState: DetalleLocalUiState,
    esFavorito: Boolean,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
    onFavoritoClick: () -> Unit,
    onPromocionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            DetalleTopBar(
                titulo = (uiState as? DetalleLocalUiState.Success)?.local?.nombre
                    ?: stringResource(R.string.detalle_local_titulo),
                onVolver = onVolver,
                acciones = {
                    if (uiState is DetalleLocalUiState.Success) {
                        BotonFavorito(esFavorito = esFavorito, onClick = onFavoritoClick)
                    }
                }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            DetalleLocalUiState.Loading -> LoadingContent(contenidoModifier)
            DetalleLocalUiState.Error -> MensajeContent(
                icono = Icons.Outlined.CloudOff,
                mensaje = stringResource(R.string.detalle_local_error),
                onReintentar = onReintentar,
                modifier = contenidoModifier
            )
            DetalleLocalUiState.NoEncontrado -> MensajeContent(
                icono = Icons.Outlined.Storefront,
                mensaje = stringResource(R.string.detalle_local_no_encontrado),
                onReintentar = null,
                modifier = contenidoModifier
            )
            is DetalleLocalUiState.Success -> SuccessContent(
                state = uiState,
                onPromocionClick = onPromocionClick,
                modifier = contenidoModifier
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.detalle_local_cargando), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Error o local no encontrado. Sin [onReintentar] no se muestra el botón. */
@Composable
private fun MensajeContent(
    icono: ImageVector,
    mensaje: String,
    onReintentar: (() -> Unit)?,
    modifier: Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(mensaje, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onReintentar != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReintentar) { Text(stringResource(R.string.detalle_reintentar)) }
        }
    }
}

@Composable
private fun SuccessContent(
    state: DetalleLocalUiState.Success,
    onPromocionClick: (String) -> Unit,
    modifier: Modifier
) {
    val local = state.local
    // Color de relleno mientras carga la imagen, si falla o si el local no tiene foto.
    val sinImagen = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "imagen") {
            AsyncImage(
                model = local.imagenUrl, // URL pública de Supabase Storage
                contentDescription = stringResource(R.string.detalle_local_foto, local.nombre),
                contentScale = ContentScale.Crop,
                placeholder = sinImagen,
                error = sinImagen,
                fallback = sinImagen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        item(key = "cabecera") { Cabecera(local) }

        item(key = "info") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoFila(
                    icono = Icons.Outlined.Schedule,
                    titulo = stringResource(R.string.detalle_local_horario),
                    texto = local.horario ?: stringResource(R.string.detalle_local_horario_no_informado)
                )
                Ubicacion(local)
            }
        }

        item(key = "divisor_menu") { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }

        // ---- Menú destacado ----
        item(key = "menu_titulo") { SectionHeader(stringResource(R.string.detalle_local_menu)) }
        if (state.platosDestacados.isEmpty()) {
            item(key = "menu_vacio") { EmptyMessage(stringResource(R.string.detalle_local_menu_vacio)) }
        } else {
            items(state.platosDestacados, key = { it.id }) { plato -> PlatoFila(plato) }
        }

        // ---- Promociones activas ----
        item(key = "promos_titulo") { SectionHeader(stringResource(R.string.detalle_local_promociones)) }
        if (state.promociones.isEmpty()) {
            item(key = "promos_vacio") { EmptyMessage(stringResource(R.string.detalle_local_promociones_vacio)) }
        } else {
            items(state.promociones, key = { it.id }) { promo ->
                PromocionCard(
                    promocion = promo,
                    onClick = { onPromocionClick(promo.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun Cabecera(local: LocalGastronomico) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(local.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Star,
                contentDescription = stringResource(R.string.detalle_local_valoracion),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = String.format(LocaleCL, "%.1f", local.valoracion),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = " · ${local.categoriaNombre} · ${local.comuna}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (local.descripcion.isNotBlank()) {
            Text(
                text = local.descripcion,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Dirección y botón "Cómo llegar", que abre la app de mapas con las coordenadas del local. */
@Composable
private fun Ubicacion(local: LocalGastronomico) {
    val uriHandler = LocalUriHandler.current
    val lat = local.latitud
    val lon = local.longitud

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoFila(
            icono = Icons.Filled.LocationOn,
            titulo = stringResource(R.string.detalle_local_ubicacion),
            texto = listOfNotNull(local.direccion, local.comuna).joinToString(", ")
        )
        if (lat != null && lon != null) {
            OutlinedButton(
                // Si no hay app de mapas ni navegador, el botón simplemente no hace nada.
                onClick = { runCatching { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lon") } },
                modifier = Modifier.padding(start = 32.dp)
            ) {
                Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detalle_local_como_llegar))
            }
        }
    }
}

@Composable
private fun InfoFila(icono: ImageVector, titulo: String, texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icono,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlatoFila(plato: Plato) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(plato.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (plato.descripcion.isNotBlank()) {
                Text(
                    plato.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            plato.precioTexto(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionHeader(titulo: String) {
    Text(
        titulo,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyMessage(texto: String) {
    Text(
        text = texto,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
