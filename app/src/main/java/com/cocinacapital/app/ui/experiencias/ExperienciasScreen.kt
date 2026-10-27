package com.cocinacapital.app.ui.experiencias

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Experiencia
import com.cocinacapital.app.data.model.precioReferencialTexto
import com.cocinacapital.app.ui.detalle.components.DetalleTopBar

@Composable
fun ExperienciasScreen(onVolver: () -> Unit, onDetalle: (String) -> Unit,
    model: ExperienciasViewModel = viewModel(factory = ExperienciasViewModel.Factory)) {
    val state by model.uiState.collectAsStateWithLifecycle()
    ExperienciasContent(state, onVolver, onDetalle, model::onReintentar)
}

@Composable
fun ExperienciasContent(state: ExperienciasUiState<List<Experiencia>>, onVolver: () -> Unit,
    onDetalle: (String) -> Unit, onReintentar: () -> Unit) {
    Scaffold(topBar = {
        BarraExperiencias(R.string.exp_titulo, onVolver, onReintentar, state !is ExperienciasUiState.Loading)
    }) { padding ->
        val contenido = Modifier.fillMaxSize().padding(padding)
        when (state) {
            ExperienciasUiState.Loading -> CargandoExperiencias(contenido)
            ExperienciasUiState.Error -> MensajeExperiencias(R.string.exp_error, contenido, onReintentar)
            is ExperienciasUiState.Success -> if (state.datos.isEmpty()) {
                MensajeExperiencias(R.string.exp_vacio, contenido, onReintentar)
            } else {
                LazyColumn(contenido, state = rememberLazyListState(),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(stringResource(R.string.exp_subtitulo),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.datos, key = { it.id }) { experiencia ->
                        OutlinedCard(onClick = { onDetalle(experiencia.id) }, modifier = Modifier.fillMaxWidth()) {
                            FotoExperiencia(experiencia)
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                experiencia.local?.let { Text(it.nombre, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary) }
                                Text(experiencia.titulo, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.SemiBold)
                                experiencia.descripcion.takeIf { it.isNotBlank() }?.let {
                                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(experiencia.local?.comuna.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                Text(experiencia.horario?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.exp_horario_pendiente), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.exp_precio_resumen, precioExperiencia(experiencia)),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetalleExperienciaScreen(id: String, onVolver: () -> Unit, onLocal: (String) -> Unit,
    model: DetalleExperienciaViewModel = viewModel(factory = DetalleExperienciaViewModel.factory(id))) {
    val state by model.uiState.collectAsStateWithLifecycle()
    DetalleExperienciaContent(state, onVolver, onLocal, model::onReintentar)
}

@Composable
fun DetalleExperienciaContent(state: ExperienciasUiState<Experiencia?>, onVolver: () -> Unit,
    onLocal: (String) -> Unit, onReintentar: () -> Unit) {
    val experiencia = (state as? ExperienciasUiState.Success)?.datos
    Scaffold(topBar = {
        BarraExperiencias(R.string.exp_detalle, onVolver, onReintentar, state !is ExperienciasUiState.Loading)
    }, bottomBar = {
        experiencia?.local?.let { local ->
            Surface(tonalElevation = 2.dp) {
                Button(onClick = { onLocal(local.id) },
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.detalle_promocion_ver_local))
                }
            }
        }
    }) { padding ->
        val contenido = Modifier.fillMaxSize().padding(padding)
        when (state) {
            ExperienciasUiState.Loading -> CargandoExperiencias(contenido)
            ExperienciasUiState.Error -> MensajeExperiencias(R.string.exp_error_detalle, contenido, onReintentar)
            is ExperienciasUiState.Success -> if (experiencia == null) {
                MensajeExperiencias(R.string.exp_no_encontrada, contenido, onReintentar)
            } else {
                LazyColumn(contenido, contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { FotoExperiencia(experiencia) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            experiencia.local?.let { Text(it.nombre, color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall) }
                            Text(experiencia.titulo, style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.SemiBold)
                            Text(experiencia.descripcion.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.exp_descripcion_pendiente))
                        }
                    }
                    item { BloqueExperiencia(R.string.exp_horario) {
                        Text(experiencia.horario?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.exp_horario_pendiente))
                    } }
                    item { BloqueExperiencia(R.string.exp_precio) {
                        Text(precioExperiencia(experiencia), style = MaterialTheme.typography.titleLarge)
                    } }
                    item { BloqueExperiencia(R.string.detalle_promocion_local) {
                        experiencia.local?.let { local ->
                            Text(local.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(local.comuna)
                            Text(local.direccion?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.detalle_promocion_direccion_pendiente))
                        } ?: Text(stringResource(R.string.detalle_promocion_local_no_disponible))
                    } }
                }
            }
        }
    }
}

@Composable
private fun BarraExperiencias(@StringRes titulo: Int, onVolver: () -> Unit,
    onReintentar: () -> Unit, habilitado: Boolean) {
    DetalleTopBar(stringResource(titulo), onVolver, acciones = {
        IconButton(onClick = onReintentar, enabled = habilitado) {
            Icon(Icons.Outlined.Refresh, stringResource(R.string.exp_actualizar))
        }
    })
}

@Composable
private fun CargandoExperiencias(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun MensajeExperiencias(@StringRes mensaje: Int, modifier: Modifier, onReintentar: () -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item { Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        item { Text(stringResource(mensaje)) }
        item { Button(onClick = onReintentar) { Text(stringResource(R.string.detalle_reintentar)) } }
    }
}

@Composable
private fun FotoExperiencia(experiencia: Experiencia) {
    experiencia.imagenUrl?.takeIf { it.isNotBlank() }?.let { url ->
        val fondo = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest)
        Card(Modifier.fillMaxWidth()) {
            AsyncImage(model = url, contentDescription = stringResource(R.string.exp_foto, experiencia.titulo),
                placeholder = fondo, error = fondo, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(160.dp))
        }
    }
}

@Composable
private fun BloqueExperiencia(@StringRes titulo: Int, contenido: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

@Composable
private fun precioExperiencia(experiencia: Experiencia): String =
    experiencia.precioReferencialTexto() ?: stringResource(R.string.exp_precio_pendiente)
