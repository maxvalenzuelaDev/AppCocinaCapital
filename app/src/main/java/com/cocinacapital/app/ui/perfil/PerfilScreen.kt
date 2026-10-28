package com.cocinacapital.app.ui.perfil

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.cocinacapital.app.BuildConfig
import com.cocinacapital.app.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import com.cocinacapital.app.ui.legal.DocumentoLegal
import com.cocinacapital.app.ui.legal.DocumentoLegalDialog
import com.cocinacapital.app.ui.legal.EnlacesLegales
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import com.cocinacapital.app.ui.perfil.components.Avatar
import com.cocinacapital.app.ui.perfil.components.Estadistica
import com.cocinacapital.app.ui.perfil.components.FotoPerfilDialogos
import com.cocinacapital.app.ui.perfil.components.SeccionCard
import com.cocinacapital.app.ui.perfil.components.Separador
import com.cocinacapital.app.ui.perfil.components.SwitchRow
import java.text.DateFormat
import java.util.Date

/** Pantalla Perfil con estado: conecta el ViewModel con la UI. */
@Composable
fun PerfilScreen(
    modifier: Modifier = Modifier,
    viewModel: PerfilViewModel = viewModel(factory = PerfilViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val foto by viewModel.foto.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.actualizarCanjeados() }

    // ---- Foto de perfil (T-23): la pantalla solo lanza lo que pide el ViewModel ----
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        viewModel.onFotoTomada(exito)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onFotoElegida(uri?.toString())
    }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        viewModel.onResultadoPermisoCamara(concedido)
    }

    LaunchedEffect(foto.accion) {
        when (val accion = foto.accion ?: return@LaunchedEffect) {
            AccionFoto.PedirPermisoCamara -> permisoCamara.launch(Manifest.permission.CAMERA)
            is AccionFoto.AbrirCamara -> camara.launch(Uri.parse(accion.uri))
            AccionFoto.AbrirGaleria -> galeria.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            AccionFoto.AbrirAjustes -> runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
        }
        viewModel.onAccionFotoRealizada()
    }

    val alElegirCamara = {
        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onElegirCamara(concedido)
    }

    FotoPerfilDialogos(
        dialogo = foto.dialogo,
        tieneFoto = (uiState as? PerfilUiState.Success)?.preferencias?.fotoPerfilRuta != null,
        onTomarFoto = alElegirCamara,
        onElegirGaleria = viewModel::onElegirGaleria,
        onQuitarFoto = viewModel::onQuitarFoto,
        onAceptarExplicacion = viewModel::onAceptarExplicacionPermiso,
        onAbrirAjustes = viewModel::onAbrirAjustes,
        onCerrar = viewModel::onCerrarDialogoFoto
    )

    // Android 13+ exige pedir el permiso de notificaciones en tiempo de ejecución.
    val permisoNotificaciones = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> viewModel.onNotificaciones(concedido) }

    val alCambiarNotificaciones: (Boolean) -> Unit = { activar ->
        if (activar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onNotificaciones(activar)
        }
    }

    // Perfil no tiene barra superior: sin este margen, el contenido queda bajo la barra de estado
    // (hora, batería) y la cámara perforada de la pantalla, y se ve detrás de ellas al hacer scroll.
    val contenidoModifier = modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )

    val snackbar = remember { SnackbarHostState() }
    val eliminandoCuenta by viewModel.eliminandoCuenta.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.mensajes.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            PerfilUiState.Loading -> Column(
                modifier = contenidoModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            is PerfilUiState.Error -> Column(
                modifier = contenidoModifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(state.mensajeRes), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::onReintentar) { Text(stringResource(R.string.perfil_reintentar)) }
            }

            is PerfilUiState.Success -> PerfilContent(
                state = state,
                alCambiarFoto = viewModel::onCambiarFoto,
                alGuardarNombre = viewModel::onGuardarNombre,
                alElegirCategoria = viewModel::onCategoriaClick,
                alElegirRango = viewModel::onRangoPrecio,
                alQuitarFavorito = viewModel::onQuitarFavorito,
                alCambiarNotificaciones = alCambiarNotificaciones,
                alCambiarSoloCercanas = viewModel::onSoloCercanas,
                alCambiarSoloCategorias = viewModel::onSoloCategoriasFavoritas,
                alCambiarConsentimiento = viewModel::onConsentimiento,
                alBorrarHistorial = viewModel::onBorrarHistorial,
                alEliminarCuenta = viewModel::onEliminarCuenta,
                alElegirTema = viewModel::onTema,
                alCerrarSesion = viewModel::onCerrarSesion,
                modifier = contenidoModifier,
                eliminandoCuenta = eliminandoCuenta
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PerfilContent(
    state: PerfilUiState.Success,
    alCambiarFoto: () -> Unit,
    alGuardarNombre: (String) -> Unit,
    alElegirCategoria: (String) -> Unit,
    alElegirRango: (RangoPrecio) -> Unit,
    alQuitarFavorito: (com.cocinacapital.app.data.model.Favorito) -> Unit,
    alCambiarNotificaciones: (Boolean) -> Unit,
    alCambiarSoloCercanas: (Boolean) -> Unit,
    alCambiarSoloCategorias: (Boolean) -> Unit,
    alCambiarConsentimiento: (Boolean) -> Unit,
    alBorrarHistorial: () -> Unit,
    alEliminarCuenta: () -> Unit,
    alElegirTema: (TemaApp) -> Unit,
    alCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
    eliminandoCuenta: Boolean = false
) {
    val prefs = state.preferencias
    var editandoNombre by rememberSaveable { mutableStateOf(false) }
    var confirmarBorrado by rememberSaveable { mutableStateOf(false) }
    var confirmarEliminar by rememberSaveable { mutableStateOf(false) }
    var documentoAbierto by rememberSaveable { mutableStateOf<DocumentoLegal?>(null) }
    documentoAbierto?.let { DocumentoLegalDialog(it, onCerrar = { documentoAbierto = null }) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Cabecera ----
        item {
            state.usuario?.let { usuario ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(usuario, fotoRuta = prefs.fotoPerfilRuta, onClick = alCambiarFoto)
                    Spacer(Modifier.height(8.dp))
                    Text(usuario.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        usuario.correo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { editandoNombre = true }) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.perfil_editar))
                    }
                    val estadisticas = listOf(
                        "${state.canjeados}" to stringResource(R.string.perfil_stat_canjeados),
                        "${state.favoritos.size}" to stringResource(R.string.perfil_stat_favoritos),
                        "${prefs.categoriasIds.size}" to stringResource(R.string.perfil_stat_cocinas)
                    )
                    // Con la fuente del sistema muy grande, tres columnas parten las palabras: se apilan (T-18).
                    if (LocalDensity.current.fontScale >= 1.5f) {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            estadisticas.forEach { (valor, etiqueta) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.size(12.dp))
                                    Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            estadisticas.forEach { (valor, etiqueta) -> Estadistica(valor, etiqueta, Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // ---- Preferencias gastronómicas ----
        item {
            SeccionCard(stringResource(R.string.perfil_preferencias_titulo), descripcion = stringResource(R.string.perfil_preferencias_descripcion)) {
                Text(stringResource(R.string.perfil_tipos_cocina), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.categorias.forEach { categoria ->
                        FilterChip(
                            selected = categoria.id in prefs.categoriasIds,
                            onClick = { alElegirCategoria(categoria.id) },
                            label = { Text(categoria.nombre) }
                        )
                    }
                }
                Separador()
                Text(stringResource(R.string.perfil_rango_precios), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RangoPrecio.entries.forEachIndexed { i, rango ->
                        SegmentedButton(
                            selected = prefs.rangoPrecio == rango,
                            onClick = { alElegirRango(rango) },
                            shape = SegmentedButtonDefaults.itemShape(i, RangoPrecio.entries.size)
                        ) { Text("${rango.simbolo} ${stringResource(rango.etiquetaRes)}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }

        // ---- Favoritos ----
        item {
            SeccionCard(stringResource(R.string.perfil_favoritos)) {
                if (state.favoritos.isEmpty()) {
                    Text(
                        stringResource(R.string.perfil_favoritos_vacio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.favoritos.forEach { fav ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fav.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    fav.subtitulo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { alQuitarFavorito(fav) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.perfil_quitar_favorito, fav.nombre))
                            }
                        }
                    }
                }
            }
        }

        // ---- Notificaciones ----
        item {
            SeccionCard(stringResource(R.string.perfil_notificaciones)) {
                SwitchRow(
                    stringResource(R.string.perfil_notif_promociones), prefs.notificaciones, alCambiarNotificaciones,
                    descripcion = stringResource(R.string.perfil_notif_promociones_descripcion)
                )
                SwitchRow(
                    stringResource(R.string.perfil_notif_cerca), prefs.soloCercanas, alCambiarSoloCercanas,
                    enabled = prefs.notificaciones
                )
                SwitchRow(
                    stringResource(R.string.perfil_notif_cocinas), prefs.soloCategoriasFavoritas, alCambiarSoloCategorias,
                    enabled = prefs.notificaciones
                )
            }
        }

        // ---- Privacidad ----
        item {
            SeccionCard(stringResource(R.string.perfil_privacidad), descripcion = stringResource(R.string.perfil_privacidad_leyes)) {
                SwitchRow(
                    stringResource(R.string.perfil_personalizar),
                    prefs.consentimiento,
                    alCambiarConsentimiento,
                    descripcion = stringResource(R.string.perfil_personalizar_descripcion)
                )
                prefs.consentimientoFecha?.let {
                    Text(
                        stringResource(R.string.perfil_aceptado_el, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Separador()
                OutlinedButton(onClick = { confirmarBorrado = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.perfil_borrar_historial))
                }
                OutlinedButton(
                    onClick = { confirmarEliminar = true },
                    enabled = !eliminandoCuenta,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (eliminandoCuenta) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.perfil_eliminar_cuenta), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ---- Aplicación ----
        item {
            SeccionCard(stringResource(R.string.perfil_aplicacion)) {
                Text(stringResource(R.string.perfil_tema), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TemaApp.entries.forEachIndexed { i, tema ->
                        SegmentedButton(
                            selected = prefs.tema == tema,
                            onClick = { alElegirTema(tema) },
                            shape = SegmentedButtonDefaults.itemShape(i, TemaApp.entries.size)
                        ) { Text(stringResource(tema.etiquetaRes), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Separador()
                Text(
                    stringResource(R.string.perfil_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // T-16: política de privacidad y términos de uso.
                EnlacesLegales(onAbrir = { documentoAbierto = it })
            }
        }

        item {
            Button(
                onClick = alCerrarSesion,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.perfil_cerrar_sesion))
            }
        }
    }

    if (editandoNombre && state.usuario != null) {
        EditarNombreDialog(
            nombreActual = state.usuario.nombre,
            onGuardar = { alGuardarNombre(it); editandoNombre = false },
            onCancelar = { editandoNombre = false }
        )
    }
    if (confirmarBorrado) {
        ConfirmDialog(
            titulo = stringResource(R.string.perfil_borrar_titulo),
            mensaje = stringResource(R.string.perfil_borrar_mensaje),
            confirmar = stringResource(R.string.perfil_borrar_confirmar),
            onConfirmar = { alBorrarHistorial(); confirmarBorrado = false },
            onCancelar = { confirmarBorrado = false }
        )
    }
    if (confirmarEliminar) {
        ConfirmDialog(
            titulo = stringResource(R.string.perfil_eliminar_titulo),
            mensaje = stringResource(R.string.perfil_eliminar_mensaje),
            confirmar = stringResource(R.string.perfil_eliminar_confirmar),
            onConfirmar = { alEliminarCuenta(); confirmarEliminar = false },
            onCancelar = { confirmarEliminar = false }
        )
    }
}

@Composable
private fun EditarNombreDialog(nombreActual: String, onGuardar: (String) -> Unit, onCancelar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf(nombreActual) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.perfil_editar)) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text(stringResource(R.string.perfil_nombre)) },
                singleLine = true,
                isError = texto.trim().length < 2,
                supportingText = { if (texto.trim().length < 2) Text(stringResource(R.string.perfil_nombre_minimo)) }
            )
        },
        confirmButton = { TextButton(onClick = { onGuardar(texto) }, enabled = texto.trim().length >= 2) { Text(stringResource(R.string.perfil_guardar)) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text(stringResource(R.string.perfil_cancelar)) } }
    )
}

@Composable
private fun ConfirmDialog(
    titulo: String,
    mensaje: String,
    confirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text(stringResource(R.string.perfil_cancelar)) } }
    )
}
