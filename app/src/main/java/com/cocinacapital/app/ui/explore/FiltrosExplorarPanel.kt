package com.cocinacapital.app.ui.explore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.PresupuestoPersona
import com.cocinacapital.app.data.model.ErrorPresupuesto
import com.cocinacapital.app.data.model.leerPresupuesto
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first

@Composable internal fun presupuestoLabel(presupuesto: PresupuestoPersona): String = when {
    presupuesto.maximo == null -> stringResource(R.string.ex_presupuesto_desde_valor, monedaCL(presupuesto.minimo))
    presupuesto.minimo == 0L -> stringResource(R.string.ex_presupuesto_hasta_valor, monedaCL(presupuesto.maximo))
    else -> stringResource(R.string.ex_presupuesto_rango, monedaCL(presupuesto.minimo), monedaCL(presupuesto.maximo))
}

/** El mismo acceso y borrador de filtros en la lista y en el mapa. Cancelar no cambia los resultados. */
@Composable internal fun ExploreSearchFilters(state: ExplorarUiState.Success, model: ExplorarViewModel,
    solicitarFoco: Boolean = false, onFocoSolicitado: () -> Unit = {}, onPreferences: () -> Unit,
    onBack: (() -> Unit)? = null) {
    var abierto by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(solicitarFoco, owner) {
        if (solicitarFoco) {
            owner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            foco.requestFocus(); teclado?.show(); onFocoSolicitado()
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchBox(state.filtros.texto, model::texto, Modifier.weight(1f).focusRequester(foco), onBack = onBack)
        ExploreFilterButton(state.filtros.cantidadActivos) { focusManager.clearFocus(); teclado?.hide(); abierto = true }
    }
    if (abierto) FiltrosExplorarPanel(state, onDismiss = { abierto = false }, onPreferences = { abierto = false; onPreferences() }, onApply = {
        model.aplicarFiltros(it); abierto = false
    })
}

@Composable internal fun ExploreFilterButton(count: Int, onClick: () -> Unit) {
        val etiqueta = stringResource(if (count == 0) R.string.ex_filtros else R.string.ex_filtros_cantidad, count)
        Surface(onClick = onClick,
            modifier = Modifier.size(56.dp).semantics { contentDescription = etiqueta }, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
            Box(contentAlignment = Alignment.Center) {
                BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
}

@Composable internal fun FiltrosActivos(state: ExplorarUiState.Success, model: ExplorarViewModel) {
    if (state.filtros.cantidadActivos > 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        if (state.filtros.presupuesto != null) InputChip(selected = true, onClick = { model.presupuesto(null) }, modifier = Modifier.weight(1f, fill = false),
            label = { Text(presupuestoLabel(state.filtros.presupuesto)) },
            trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.ex_quitar_presupuesto), Modifier.size(16.dp)) })
        else Spacer(Modifier.width(1.dp))
        TextButton(onClick = model::limpiarFiltros) { Text(stringResource(R.string.ex_limpiar_filtros)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun FiltrosExplorarPanel(state: ExplorarUiState.Success, onDismiss: () -> Unit, onPreferences: () -> Unit,
    onApply: (FiltrosExplorar) -> Unit) {
    var borrador by remember { mutableStateOf(state.filtros) }
    var desde by remember { mutableStateOf(state.filtros.presupuesto?.minimo?.takeIf { it > 0 }?.toString().orEmpty()) }
    var hasta by remember { mutableStateOf(state.filtros.presupuesto?.maximo?.toString().orEmpty()) }
    val entrada = leerPresupuesto(desde, hasta)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().imePadding()
            .padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ex_filtros), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.ex_cerrar_filtros)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ex_presupuesto_persona), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ex_presupuesto_criterio), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(desde, { desde = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.ex_presupuesto_desde)) },
                        placeholder = { Text(stringResource(R.string.ex_presupuesto_sin_minimo)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = entrada.error != null)
                    OutlinedTextField(hasta, { hasta = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.ex_presupuesto_hasta)) },
                        placeholder = { Text(stringResource(R.string.ex_presupuesto_sin_maximo)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = entrada.error != null)
                }
                if (entrada.error != null) Text(stringResource(if (entrada.error == ErrorPresupuesto.ORDEN_INVALIDO)
                    R.string.ex_presupuesto_orden_error else R.string.ex_presupuesto_importe_error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { desde = ""; hasta = "" }) { Text(stringResource(R.string.ex_presupuesto_sin_limite)) }
                HorizontalDivider()
                Text(stringResource(R.string.ex_distancia_maxima), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { radio ->
                        FilterChip(borrador.radioKm == radio, { borrador = borrador.copy(radioKm = radio) },
                            label = { Text(stringResource(R.string.ex_hasta_km, radio)) })
                    }
                }
                Text(stringResource(R.string.ex_distancia_criterio), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text(stringResource(R.string.ex_tipo_cocina), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(borrador.categoriaId == null, { borrador = borrador.copy(categoriaId = null) }, label = { Text(stringResource(R.string.ex_todos)) })
                    state.categorias.forEach { categoria ->
                        FilterChip(borrador.categoriaId == categoria.id, { borrador = borrador.copy(categoriaId = categoria.id) }, label = { Text(categoria.nombre) })
                    }
                }
                val misCocinas = stringResource(R.string.ex_mis_cocinas)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(misCocinas, Modifier.weight(1f))
                    Switch(checked = borrador.soloPreferidas, onCheckedChange = { borrador = borrador.copy(soloPreferidas = it) },
                        modifier = Modifier.semantics { contentDescription = misCocinas },
                        enabled = state.preferidasDisponibles || borrador.soloPreferidas)
                }
                TextButton(onClick = onPreferences) { Text(stringResource(if (state.preferidasDisponibles)
                    R.string.ex_revisar_preferencias else R.string.ex_elegir_cocinas)) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { borrador = FiltrosExplorar(zonaManual = borrador.zonaManual); desde = ""; hasta = "" }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ex_restablecer))
                }
                Button(onClick = { onApply(borrador.copy(presupuesto = entrada.presupuesto)) }, enabled = entrada.error == null,
                    modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ex_aplicar_filtros)) }
            }
        }
    }
}
