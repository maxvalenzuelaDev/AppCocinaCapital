package com.cocinacapital.app.ui.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.cocinacapital.app.R
import com.cocinacapital.app.ui.theme.LocalDarkTheme
import com.cocinacapital.app.data.model.PuntoRuta
import com.cocinacapital.app.util.urlDirecciones
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ExploreFullMap(state: ExplorarUiState.Success, model: ExplorarViewModel,
    snackbar: SnackbarHostState, ubicacionEnCurso: Boolean,
    onClose: () -> Unit, onLocation: () -> Unit, onDetail: (String) -> Unit, onPreferences: () -> Unit,
    rutas: RutaViewModel = viewModel(factory = RutaViewModel.Factory)) {
    // Android 8–9 necesita reservar las barras del sistema en la ventana del diálogo.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = android.os.Build.VERSION.SDK_INT < 29)) {
        val view = LocalView.current
        val dark = LocalDarkTheme.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        } }
        val scope = rememberCoroutineScope()
        val selected = state.seleccionado
        val sheet = rememberSaveable(saver = AnchoredDraggableState.Saver<PosicionPanelMapa>()) {
            AnchoredDraggableState(if (selected == null) PosicionPanelMapa.MINIMO else PosicionPanelMapa.MEDIO)
        }
        var abrirLista by remember { mutableStateOf(false) }
        val routeState by rutas.estado.collectAsStateWithLifecycle()
        val owner = LocalLifecycleOwner.current
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        LaunchedEffect(selected, state.origen, state.filtros.zonaManual) {
            rutas.configurar(selected, state.origen, state.filtros.zonaManual)
        }
        DisposableEffect(owner, rutas) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) rutas.cancelar() }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); rutas.cancelar() }
        }
        val origin = state.origen?.let { PuntoRuta(it.latitud, it.longitud) }
        val routeUi = routeState.takeIf { it.localId == selected?.id &&
            (it.solicitud == null || (!state.filtros.zonaManual && it.solicitud?.origen == origin)) }
            ?: ContextoRuta(localId = selected?.id, modo = routeState.modo).let { contexto ->
                when {
                    selected == null -> RutaUiState.SinLocal(contexto)
                    origin == null || state.filtros.zonaManual -> RutaUiState.SinUbicacion(contexto)
                    else -> RutaUiState.Pendiente(contexto)
                }
            }
        val routeReady = selected != null && routeUi is RutaUiState.Success
        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        BackHandler { if (selected != null) model.seleccionar(null) else onClose() }
        LaunchedEffect(selected?.id) {
            sheet.animateTo(if (abrirLista) PosicionPanelMapa.AMPLIO else if (selected == null) PosicionPanelMapa.MINIMO else PosicionPanelMapa.MEDIO)
            abrirLista = false
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val visiblePanel = when (sheet.settledValue) {
                PosicionPanelMapa.MINIMO -> 88.dp + navigationInset
                PosicionPanelMapa.MEDIO -> maxHeight * .5f
                PosicionPanelMapa.AMPLIO -> maxHeight * .82f
            }
            PanelMapa(sheet, maxHeight, 88.dp + navigationInset, selected?.nombre ?: localCount(state.locales.size), Modifier.zIndex(2f)) {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selected != null) {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.ex_seleccionado), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { model.seleccionar(null) }) { Icon(Icons.Outlined.Close, stringResource(R.string.ex_quitar_seleccion)) }
                                }
                                if (!routeReady) {
                                    Text(stringResource(R.string.ex_categoria_comuna, selected.categoriaNombre, selected.comuna))
                                    Text(stringResource(R.string.ex_valoracion_distancia, rating(selected), distanceLabel(selected)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(preciosLabel(selected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item { RutaPanel(routeUi, rutas::calcular, onLocation, rutas::modo,
                                navegar = {
                                    routeUi.solicitud?.let { request ->
                                        runCatching { uriHandler.openUri(urlDirecciones(request.destino, request.origen, request.modo)) }
                                            .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.ruta_no_maps)) } }
                                    }
                                }, detalle = { onDetail(selected.id) }) }
                            if (!routeReady) item { OutlinedButton(onClick = { onDetail(selected.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ex_detalle_completo)) } }
                            item { TextButton(onClick = { abrirLista = true; model.seleccionar(null) }) { Text(stringResource(R.string.ex_todos_cercanos)) } }
                        } else {
                            item {
                                Text(stringResource(if (state.filtros.zonaManual) R.string.ex_zona_radio else R.string.ex_hasta_km,
                                    state.filtros.radioKm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.origen == null) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.ex_pedir_ubicacion))
                                TextButton(onClick = model::elegirVina) { Text(stringResource(R.string.ex_elegir_vina)) }
                            } }
                            else if (state.locales.isEmpty()) item { Column {
                                Text(stringResource(R.string.ex_sin_resultados, state.filtros.radioKm))
                                TextButton(onClick = model::limpiarFiltros) { Text(stringResource(R.string.ex_limpiar_filtros)) }
                                if (state.soloPreferidas) TextButton(onClick = onPreferences) { Text(stringResource(R.string.ex_revisar_preferencias)) }
                            } }
                            else items(state.locales, key = { it.id }) { local -> LocalRow(local) { model.seleccionar(local.id) } }
                        }
                    }
            }
                Box(Modifier.fillMaxSize()) {
                    ExploreMap(state.locales, state.origen, selected?.id, onSelected = model::seleccionar,
                        ruta = if (routeReady) routeUi.ruta else null, compacto = routeReady,
                        onCleared = { model.seleccionar(null) }, positionLabel = stringResource(if (state.filtros.zonaManual) R.string.ex_ubicacion_elegida else R.string.gps_titulo),
                        bottomInsetDp = visiblePanel.value.roundToInt() + 8,
                        topInsetDp = if (routeReady) 174 else 228, modifier = Modifier.fillMaxSize())
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(onClick = onClose, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ex_cerrar_mapa), Modifier.padding(14.dp))
                            }
                            Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp, modifier = Modifier.weight(1f)) {
                                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(if (state.filtros.zonaManual) R.string.ex_ubicacion_elegida else if (state.origen == null) R.string.ex_ubicacion_pendiente else R.string.gps_titulo), style = MaterialTheme.typography.labelLarge)
                                        Text(stringResource(if (state.filtros.zonaManual) R.string.ex_vina else R.string.ex_locales_cerca), style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = onLocation, enabled = !ubicacionEnCurso) {
                                        if (ubicacionEnCurso) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Outlined.MyLocation, stringResource(R.string.ex_usar_gps))
                                    }
                                    RadiusMenu(state.filtros.radioKm, model::radio)
                                }
                            }
                        }
                        if (!routeReady) Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 4.dp) {
                            ExploreSearchFilters(state, model, onPreferences = onPreferences)
                        }
                    }
                }
            SnackbarHost(snackbar, Modifier.zIndex(3f).align(Alignment.TopCenter).statusBarsPadding()
                .padding(top = 156.dp, start = 16.dp, end = 16.dp))
        }
    }
}
