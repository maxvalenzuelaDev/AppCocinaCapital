package com.cocinacapital.app.ui.explore

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R

@Composable
fun ExploreScreen(onLocation: () -> Unit, onLocalDetail: (String) -> Unit, onPreferences: () -> Unit,
    snackbar: SnackbarHostState, onExperiencias: () -> Unit = {}, ubicacionEnCurso: Boolean = false,
    solicitarBusqueda: Boolean = false, categoriaDesdeHome: String? = null, onBusquedaConsumida: () -> Unit = {},
    barraAnclada: Boolean = false, onCerrarBusqueda: () -> Unit = {},
    model: ExplorarViewModel = viewModel(factory = ExplorarViewModel.Factory)) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val filtros by model.filtrosBusqueda.collectAsStateWithLifecycle()
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    var resumeMap by rememberSaveable { mutableStateOf(false) }
    var enfocarBuscador by rememberSaveable { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    val cerrarBusqueda = { foco.clearFocus(); teclado?.hide(); onCerrarBusqueda() }
    BackHandler(barraAnclada && !mapOpen, onBack = cerrarBusqueda)
    LaunchedEffect(Unit) { if (resumeMap) { mapOpen = true; resumeMap = false } }
    LaunchedEffect(solicitarBusqueda) {
        if (solicitarBusqueda) {
            model.iniciarBusquedaDesdeHome(categoriaDesdeHome)
            mapOpen = false; resumeMap = false; enfocarBuscador = true
            onBusquedaConsumida()
        }
    }
    LaunchedEffect(enfocarBuscador, state is ExplorarUiState.Success) {
        if (enfocarBuscador && state is ExplorarUiState.Success) lista.scrollToItem(0)
    }
    val locate = { model.usarGps(); onLocation() }
    // El buscador no desaparece al cargar o reintentar el catálogo: continúa el que subió en Home.
    val s = state as? ExplorarUiState.Success ?: ExplorarUiState.Success(emptyList(), emptyList(), null,
        filtros, filtros.soloPreferidas, false)
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                AnimatedVisibility(!tecladoAbierto && !barraAnclada, enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ex_explorar_titulo), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.MyLocation, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (s.filtros.zonaManual) R.string.ex_vina else if (s.origen == null)
                                    R.string.ex_ubicacion_pendiente else R.string.ex_cerca_titulo),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = locate, enabled = !ubicacionEnCurso) {
                                    if (ubicacionEnCurso) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.Refresh, stringResource(R.string.ex_usar_gps), Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        MapLauncher { model.seleccionar(null); mapOpen = true }
                    }
                }
                Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    ExploreSearchFilters(s, model, enfocarBuscador, { enfocarBuscador = false }, onPreferences,
                        onBack = if (barraAnclada) cerrarBusqueda else null)
                }
                AnimatedVisibility(!tecladoAbierto && !barraAnclada) {
                    TextButton(onClick = onExperiencias, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.exp_entrada))
                    }
                }
                when (state) {
                    ExplorarUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ExplorarUiState.Error -> Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.ex_error))
                        Button(onClick = model::recargar) { Text(stringResource(R.string.ex_reintentar)) }
                    }
                    is ExplorarUiState.Success -> {
                LazyColumn(Modifier.weight(1f), state = lista, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "categorias") { AnimatedVisibility(!tecladoAbierto && !barraAnclada,
                    enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item { ExploreCategory(stringResource(R.string.ex_todos), s.filtros.categoriaId == null) { model.categoria(null) } }
                        items(s.categorias, key = { it.id }) { cat -> ExploreCategory(cat.nombre, s.filtros.categoriaId == cat.id) { model.categoria(cat.id) } }
                    }
                } }
                item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(localCount(s.locales.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.ex_hasta_km, s.filtros.radioKm), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                if (s.origen == null) item { Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.ex_pedir_ubicacion))
                    TextButton(onClick = locate, enabled = !ubicacionEnCurso) { Text(stringResource(R.string.ex_usar_gps)) }
                    TextButton(onClick = model::elegirVina) { Text(stringResource(R.string.ex_elegir_vina)) }
                } }
                else if (s.locales.isEmpty()) item { Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.ex_sin_resultados, s.filtros.radioKm))
                    TextButton(onClick = model::limpiarFiltros) { Text(stringResource(R.string.ex_limpiar_filtros)) }
                    if (s.soloPreferidas) TextButton(onClick = onPreferences) { Text(stringResource(R.string.ex_revisar_preferencias)) }
                    TextButton(onClick = model::recargar) { Text(stringResource(R.string.ex_recargar_catalogo)) }
                    if (!s.filtros.zonaManual) TextButton(onClick = model::elegirVina) { Text(stringResource(R.string.ex_elegir_vina)) }
                } }
                else items(s.locales, key = { it.id }) { local -> Box(Modifier.padding(horizontal = 20.dp)) {
                    LocalRow(local, compacto = true) { model.seleccionar(local.id); mapOpen = true }
                } }
                if (s.locales.isNotEmpty()) item { Text(stringResource(R.string.ex_distancias_rectas),
                    Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                    }
                }
            }
            if (mapOpen && state is ExplorarUiState.Success) ExploreFullMap(s, model, snackbar, ubicacionEnCurso, onClose = { mapOpen = false }, onLocation = locate,
                onDetail = { id -> resumeMap = true; mapOpen = false; onLocalDetail(id) },
                onPreferences = { resumeMap = true; mapOpen = false; onPreferences() })
}
