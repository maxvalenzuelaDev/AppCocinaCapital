package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import com.cocinacapital.app.data.model.LocalGastronomico
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
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.ui.location.rememberUbicacionAction
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pestaña "Mis locales" del oferente (HU-25): lista de sus tiendas con un botón para crear otra;
 * al tocar una se abre su formulario. El ViewModel lo crea OferenteScaffold, porque Catálogo y
 * Promociones también usan la lista y el local elegido.
 */
@Composable
fun MisLocalesScreen(snackbar: SnackbarHostState, viewModel: MisLocalesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel, snackbar) {
        viewModel.mensajes.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    when (val state = uiState) {
        MisLocalesUiState.Loading -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }

        is MisLocalesUiState.Error -> Column(
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

        is MisLocalesUiState.Success -> {
            val editor = state.editor
            if (editor == null) {
                ListaLocales(state.locales, state.categorias, onNuevo = viewModel::onNuevo, onEditar = viewModel::onEditar)
            } else {
                BackHandler(onBack = viewModel::onCerrarEditor)
                // Permiso, GPS apagado y errores se avisan igual que en el resto de la app (HU-17).
                val ubicacion = rememberUbicacionAction(snackbar)
                FormularioLocalView(
                    editor = editor,
                    categorias = state.categorias,
                    obteniendoUbicacion = ubicacion.cargando,
                    onUsarUbicacion = { viewModel.onUsarUbicacion(); ubicacion.solicitar() },
                    onFotoElegida = viewModel::onFotoElegida,
                    onCambio = viewModel::onCambio,
                    onGuardar = viewModel::onGuardar,
                    onCerrar = viewModel::onCerrarEditor
                )
            }
        }
    }
}

@Composable
private fun ListaLocales(
    locales: List<LocalGastronomico>,
    categorias: List<Categoria>,
    onNuevo: () -> Unit,
    onEditar: (LocalGastronomico) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Espacio abajo para que el botón flotante no tape el último local.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.mis_locales_ayuda),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (locales.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Storefront, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.mis_locales_vacio), textAlign = TextAlign.Center)
                    }
                }
            }
            items(locales, key = { it.id }) { local ->
                LocalItem(local, categorias.firstOrNull { it.id == local.categoriaId }?.nombre.orEmpty()) { onEditar(local) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNuevo,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.mis_locales_nuevo)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun LocalItem(local: LocalGastronomico, categoria: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Miniatura(local.imagenUrl, Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(local.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(categoria, local.comuna).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                local.direccion?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (local.latitud == null || local.longitud == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.mis_locales_sin_ubicacion),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.mis_locales_editar, local.nombre),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Foto del local o, si no tiene, el ícono de tienda sobre el color de fondo. */
@Composable
private fun Miniatura(url: String?, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Outlined.Storefront, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Portada que ven los consumidores en el Inicio, Explorar y el detalle (T-05). */
@Composable
private fun SeccionFoto(editor: EditorLocal, onFotoElegida: (String) -> Unit) {
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onFotoElegida(it.toString()) }
    }
    val guardado = editor.localId != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            Miniatura(editor.imagenUrl, Modifier.fillMaxSize())
            if (editor.subiendoFoto) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
        if (guardado) {
            OutlinedButton(
                onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !editor.subiendoFoto && !editor.guardando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (editor.imagenUrl.isNullOrBlank()) R.string.mis_locales_foto_agregar else R.string.mis_locales_foto_cambiar))
            }
        } else {
            Text(
                stringResource(R.string.mis_locales_foto_despues),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormularioLocalView(
    editor: EditorLocal,
    categorias: List<Categoria>,
    obteniendoUbicacion: Boolean,
    onUsarUbicacion: () -> Unit,
    onFotoElegida: (String) -> Unit,
    onCambio: (FormularioLocal.() -> FormularioLocal) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit
) {
    val form = editor.formulario
    val errores = editor.errores
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar, enabled = !editor.guardando) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.mis_locales_volver))
            }
            Text(
                stringResource(if (editor.localId == null) R.string.mi_local_titulo_nuevo else R.string.mi_local_titulo_editar),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            stringResource(R.string.mi_local_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SeccionFoto(editor, onFotoElegida)
        Campo(form.nombre, { v -> onCambio { copy(nombre = v) } }, R.string.mi_local_nombre, errores.nombre, MAX_NOMBRE)
        SelectorCategoria(categorias, form.categoriaId, errores.categoria) { id -> onCambio { copy(categoriaId = id) } }
        Campo(form.comuna, { v -> onCambio { copy(comuna = v) } }, R.string.mi_local_comuna, errores.comuna, MAX_COMUNA)
        Campo(form.direccion, { v -> onCambio { copy(direccion = v) } }, R.string.mi_local_direccion, errores.direccion, MAX_DIRECCION)
        SeccionUbicacion(form.latitud, form.longitud, editor.precisionMetros, obteniendoUbicacion, onUsarUbicacion)
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
            enabled = !editor.guardando && !editor.subiendoFoto,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
        ) {
            if (editor.guardando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (editor.localId == null) R.string.mi_local_crear else R.string.mi_local_guardar))
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
