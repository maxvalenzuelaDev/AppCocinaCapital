package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.ui.location.rememberUbicacionAction
import java.util.Locale
import kotlin.math.roundToInt

/** Pestaña "Mi local" del oferente (HU-25): registrar o editar el perfil de su local. */
@Composable
fun MiLocalScreen(
    snackbar: SnackbarHostState,
    usuarioId: String,
    // Una instancia por cuenta: si otro oferente inicia sesión en el teléfono, no ve el local anterior.
    viewModel: MiLocalViewModel = viewModel(key = "mi_local_$usuarioId", factory = MiLocalViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel, snackbar) {
        viewModel.mensajes.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    when (val state = uiState) {
        MiLocalUiState.Loading -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }

        is MiLocalUiState.Error -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(state.mensajeRes), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::onReintentar) { Text(stringResource(R.string.mi_local_reintentar)) }
        }

        is MiLocalUiState.Success -> {
            // Permiso, GPS apagado y errores se avisan igual que en el resto de la app (HU-17).
            val ubicacion = rememberUbicacionAction(snackbar)
            FormularioMiLocal(
                state = state,
                obteniendoUbicacion = ubicacion.cargando,
                onUsarUbicacion = { viewModel.onUsarUbicacion(); ubicacion.solicitar() },
                onCambio = viewModel::onCambio,
                onGuardar = viewModel::onGuardar
            )
        }
    }
}

@Composable
private fun FormularioMiLocal(
    state: MiLocalUiState.Success,
    obteniendoUbicacion: Boolean,
    onUsarUbicacion: () -> Unit,
    onCambio: (FormularioLocal.() -> FormularioLocal) -> Unit,
    onGuardar: () -> Unit
) {
    val form = state.formulario
    val errores = state.errores
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(if (state.localId == null) R.string.mi_local_titulo_nuevo else R.string.mi_local_titulo_editar),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.mi_local_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Campo(form.nombre, { v -> onCambio { copy(nombre = v) } }, R.string.mi_local_nombre, errores.nombre, MAX_NOMBRE)
        SelectorCategoria(state.categorias, form.categoriaId, errores.categoria) { id -> onCambio { copy(categoriaId = id) } }
        Campo(form.comuna, { v -> onCambio { copy(comuna = v) } }, R.string.mi_local_comuna, errores.comuna, MAX_COMUNA)
        Campo(form.direccion, { v -> onCambio { copy(direccion = v) } }, R.string.mi_local_direccion, errores.direccion, MAX_DIRECCION)
        SeccionUbicacion(form.latitud, form.longitud, state.precisionMetros, obteniendoUbicacion, onUsarUbicacion)
        Campo(
            form.horario, { v -> onCambio { copy(horario = v) } }, R.string.mi_local_horario, errores.horario, MAX_HORARIO,
            ayudaRes = R.string.mi_local_horario_ayuda
        )
        Campo(
            form.descripcion, { v -> onCambio { copy(descripcion = v) } }, R.string.mi_local_descripcion,
            errores.descripcion, MAX_DESCRIPCION, lineas = 4
        )
        Campo(
            form.ticket, { v -> onCambio { copy(ticket = v) } }, R.string.mi_local_ticket, errores.ticket, null,
            ayudaRes = R.string.mi_local_ticket_ayuda, teclado = KeyboardType.Number
        )

        Button(
            onClick = onGuardar,
            enabled = !state.guardando,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            if (state.guardando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (state.localId == null) R.string.mi_local_crear else R.string.mi_local_guardar))
        }
    }
}

@Composable
private fun Campo(
    valor: String,
    onValor: (String) -> Unit,
    @StringRes etiquetaRes: Int,
    @StringRes errorRes: Int?,
    maximo: Int?,
    @StringRes ayudaRes: Int? = null,
    lineas: Int = 1,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(stringResource(etiquetaRes)) },
        isError = errorRes != null,
        singleLine = lineas == 1,
        minLines = lineas,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = teclado,
            imeAction = if (lineas == 1) ImeAction.Next else ImeAction.Default
        ),
        supportingText = {
            when {
                errorRes != null -> Text(stringResource(errorRes))
                ayudaRes != null -> Text(stringResource(ayudaRes))
                maximo != null && valor.length > maximo * 3 / 4 -> Text("${valor.length}/$maximo")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Posición del local para Explorar y el mapa del consumidor; se toma con el GPS estando en el local. */
@Composable
private fun SeccionUbicacion(
    latitud: Double?,
    longitud: Double?,
    precisionMetros: Float?,
    cargando: Boolean,
    onUsarUbicacion: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.mi_local_ubicacion_titulo), style = MaterialTheme.typography.titleSmall)
            if (latitud != null && longitud != null) {
                Text(
                    stringResource(
                        R.string.mi_local_ubicacion_coordenadas,
                        String.format(Locale.US, "%.5f", latitud),
                        String.format(Locale.US, "%.5f", longitud)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (precisionMetros != null) Text(
                    stringResource(R.string.mi_local_ubicacion_precision, precisionMetros.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.mi_local_ubicacion_sin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                stringResource(R.string.mi_local_ubicacion_ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onUsarUbicacion, enabled = !cargando, modifier = Modifier.fillMaxWidth()) {
                if (cargando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.mi_local_ubicacion_usar))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorCategoria(
    categorias: List<Categoria>,
    seleccionadaId: String?,
    @StringRes errorRes: Int?,
    onElegir: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = categorias.firstOrNull { it.id == seleccionadaId }?.nombre.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.mi_local_categoria)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            categorias.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.nombre) },
                    onClick = { onElegir(categoria.id); abierto = false }
                )
            }
        }
    }
}
