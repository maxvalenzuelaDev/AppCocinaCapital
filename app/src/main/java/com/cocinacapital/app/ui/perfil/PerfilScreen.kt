package com.cocinacapital.app.ui.perfil

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import com.cocinacapital.app.ui.perfil.components.Avatar
import com.cocinacapital.app.ui.perfil.components.Estadistica
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

    when (val state = uiState) {
        PerfilUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }

        is PerfilUiState.Error -> Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text(state.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::onReintentar) { Text("Reintentar") }
        }

        is PerfilUiState.Success -> PerfilContent(
            state = state,
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
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PerfilContent(
    state: PerfilUiState.Success,
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
    modifier: Modifier = Modifier
) {
    val prefs = state.preferencias
    var editandoNombre by rememberSaveable { mutableStateOf(false) }
    var confirmarBorrado by rememberSaveable { mutableStateOf(false) }
    var confirmarEliminar by rememberSaveable { mutableStateOf(false) }

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
                    Avatar(usuario)
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
                        Text("Editar perfil")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Estadistica("${state.canjeados}", "Beneficios canjeados")
                        Estadistica("${state.favoritos.size}", "Favoritos")
                        Estadistica("${prefs.categoriasIds.size}", "Cocinas preferidas")
                    }
                }
            }
        }

        // ---- Preferencias gastronómicas ----
        item {
            SeccionCard("Preferencias gastronómicas", descripcion = "Personalizan la sección \"Para ti\"") {
                Text("Tipos de cocina", style = MaterialTheme.typography.labelLarge)
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
                Text("Rango de precios", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RangoPrecio.entries.forEachIndexed { i, rango ->
                        SegmentedButton(
                            selected = prefs.rangoPrecio == rango,
                            onClick = { alElegirRango(rango) },
                            shape = SegmentedButtonDefaults.itemShape(i, RangoPrecio.entries.size)
                        ) { Text("${rango.simbolo} ${rango.etiqueta}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }

        // ---- Favoritos ----
        item {
            SeccionCard("Favoritos") {
                if (state.favoritos.isEmpty()) {
                    Text(
                        "Aún no tienes favoritos. Guarda locales y promociones para encontrarlos rápido.",
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
                                Icon(Icons.Filled.Close, contentDescription = "Quitar ${fav.nombre} de favoritos")
                            }
                        }
                    }
                }
            }
        }

        // ---- Notificaciones ----
        item {
            SeccionCard("Notificaciones") {
                SwitchRow(
                    "Promociones", prefs.notificaciones, alCambiarNotificaciones,
                    descripcion = "Avisos de ofertas afines a tu perfil"
                )
                SwitchRow(
                    "Solo cerca de mí", prefs.soloCercanas, alCambiarSoloCercanas,
                    enabled = prefs.notificaciones
                )
                SwitchRow(
                    "Solo de mis cocinas preferidas", prefs.soloCategoriasFavoritas, alCambiarSoloCategorias,
                    enabled = prefs.notificaciones
                )
            }
        }

        // ---- Privacidad ----
        item {
            SeccionCard("Privacidad y datos", descripcion = "Ley N.º 21.719 y Ley N.º 19.628") {
                SwitchRow(
                    "Personalizar con mi actividad",
                    prefs.consentimiento,
                    alCambiarConsentimiento,
                    descripcion = "Permite registrar tus búsquedas, promociones vistas y canjes para recomendarte ofertas. Puedes revocarlo cuando quieras."
                )
                prefs.consentimientoFecha?.let {
                    Text(
                        "Aceptado el " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Separador()
                OutlinedButton(onClick = { confirmarBorrado = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Borrar mi historial de actividad")
                }
                OutlinedButton(
                    onClick = { confirmarEliminar = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Eliminar mi cuenta y mis datos", color = MaterialTheme.colorScheme.error) }
            }
        }

        // ---- Aplicación ----
        item {
            SeccionCard("Aplicación") {
                Text("Tema", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TemaApp.entries.forEachIndexed { i, tema ->
                        SegmentedButton(
                            selected = prefs.tema == tema,
                            onClick = { alElegirTema(tema) },
                            shape = SegmentedButtonDefaults.itemShape(i, TemaApp.entries.size)
                        ) { Text(tema.etiqueta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Separador()
                Text(
                    "Cocina Capital · versión 1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // TODO: enlazar términos de uso y política de privacidad
            }
        }

        item {
            Button(
                onClick = alCerrarSesion,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Cerrar sesión")
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
            titulo = "¿Borrar tu historial?",
            mensaje = "Se eliminarán tus búsquedas e interacciones usadas para recomendarte ofertas.",
            confirmar = "Borrar",
            onConfirmar = { alBorrarHistorial(); confirmarBorrado = false },
            onCancelar = { confirmarBorrado = false }
        )
    }
    if (confirmarEliminar) {
        ConfirmDialog(
            titulo = "¿Eliminar tu cuenta?",
            mensaje = "Se eliminarán tu cuenta y tus datos de forma permanente. Esta acción no se puede deshacer.",
            confirmar = "Eliminar",
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
        title = { Text("Editar perfil") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Nombre") },
                singleLine = true,
                isError = texto.trim().length < 2,
                supportingText = { if (texto.trim().length < 2) Text("Mínimo 2 caracteres") }
            )
        },
        confirmButton = { TextButton(onClick = { onGuardar(texto) }, enabled = texto.trim().length >= 2) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
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
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
