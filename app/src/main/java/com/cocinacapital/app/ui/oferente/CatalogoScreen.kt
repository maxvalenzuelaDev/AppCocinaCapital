package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.precioTexto

/** Pestaña "Catálogo" del oferente (HU-26): alta, edición y baja de los platos de su local. */
@Composable
fun CatalogoScreen(
    snackbar: SnackbarHostState,
    usuarioId: String,
    onIrAMiLocal: () -> Unit,
    // Una instancia por cuenta, igual que Mi local.
    viewModel: CatalogoViewModel = viewModel(key = "catalogo_$usuarioId", factory = CatalogoViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.onMostrar() }
    LaunchedEffect(viewModel, snackbar) {
        viewModel.mensajes.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    when (val state = uiState) {
        CatalogoUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is CatalogoUiState.Error -> Mensaje(Icons.Outlined.CloudOff, state.mensajeRes, R.string.mi_local_reintentar, viewModel::onReintentar)

        CatalogoUiState.SinLocal -> Mensaje(Icons.Outlined.Storefront, R.string.catalogo_sin_local, R.string.catalogo_ir_mi_local, onIrAMiLocal)

        is CatalogoUiState.Success -> {
            ListaPlatos(state.platos, onAgregar = viewModel::onAgregar, onEditar = viewModel::onEditar, onEliminar = viewModel::onPedirEliminar)
            state.editor?.let { editor ->
                DialogoPlato(editor, state.guardando, viewModel::onCambio, viewModel::onGuardar, viewModel::onCerrarEditor)
            }
            state.porEliminar?.let { plato ->
                AlertDialog(
                    onDismissRequest = viewModel::onCancelarEliminar,
                    title = { Text(stringResource(R.string.catalogo_eliminar_titulo)) },
                    text = { Text(stringResource(R.string.catalogo_eliminar_texto, plato.nombre)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::onConfirmarEliminar, enabled = !state.guardando) {
                            Text(stringResource(R.string.catalogo_eliminar), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::onCancelarEliminar, enabled = !state.guardando) {
                            Text(stringResource(R.string.catalogo_cancelar))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ListaPlatos(
    platos: List<Plato>,
    onAgregar: () -> Unit,
    onEditar: (Plato) -> Unit,
    onEliminar: (Plato) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Espacio abajo para que el botón flotante no tape el último plato.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.catalogo_ayuda),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (platos.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.catalogo_vacio), textAlign = TextAlign.Center)
                    }
                }
            }
            items(platos, key = { it.id }) { plato -> PlatoItem(plato, { onEditar(plato) }, { onEliminar(plato) }) }
        }
        ExtendedFloatingActionButton(
            onClick = onAgregar,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.catalogo_agregar)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun PlatoItem(plato: Plato, onEditar: () -> Unit, onEliminar: () -> Unit) {
    ElevatedCard(onClick = onEditar, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plato.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (plato.descripcion.isNotBlank()) {
                    Text(plato.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plato.precioTexto(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (plato.destacado) {
                        Icon(Icons.Outlined.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(stringResource(R.string.catalogo_destacado), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            IconButton(onClick = onEditar) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.catalogo_editar_plato, plato.nombre))
            }
            IconButton(onClick = onEliminar) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.catalogo_eliminar_plato, plato.nombre))
            }
        }
    }
}

@Composable
private fun DialogoPlato(
    editor: EditorPlato,
    guardando: Boolean,
    onCambio: (EditorPlato.() -> EditorPlato) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(if (editor.platoId == null) R.string.catalogo_nuevo else R.string.catalogo_editar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.nombre,
                    onValueChange = { v -> onCambio { copy(nombre = v) } },
                    label = { Text(stringResource(R.string.catalogo_nombre)) },
                    isError = editor.errorNombre != null,
                    supportingText = editor.errorNombre?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editor.precio,
                    onValueChange = { v -> onCambio { copy(precio = v) } },
                    label = { Text(stringResource(R.string.catalogo_precio)) },
                    isError = editor.errorPrecio != null,
                    supportingText = { Text(stringResource(editor.errorPrecio ?: R.string.catalogo_precio_ayuda)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editor.descripcion,
                    onValueChange = { v -> onCambio { copy(descripcion = v) } },
                    label = { Text(stringResource(R.string.catalogo_descripcion)) },
                    isError = editor.errorDescripcion != null,
                    supportingText = editor.errorDescripcion?.let { { Text(stringResource(it)) } },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.catalogo_destacar), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.catalogo_destacar_ayuda),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = editor.destacado, onCheckedChange = { v -> onCambio { copy(destacado = v) } })
                }
            }
        },
        confirmButton = {
            Button(onClick = onGuardar, enabled = !guardando) {
                if (guardando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.catalogo_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !guardando) { Text(stringResource(R.string.catalogo_cancelar)) }
        }
    )
}

@Composable
private fun Mensaje(icono: ImageVector, @StringRes textoRes: Int, @StringRes botonRes: Int, onBoton: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(textoRes), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBoton) { Text(stringResource(botonRes)) }
    }
}
