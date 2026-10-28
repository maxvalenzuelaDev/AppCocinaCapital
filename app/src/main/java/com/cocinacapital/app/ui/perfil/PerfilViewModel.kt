package com.cocinacapital.app.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.auth.PerfilSesion
import com.cocinacapital.app.data.model.EstadoBeneficio
import com.cocinacapital.app.data.repository.BilleteraRepository
import com.cocinacapital.app.data.repository.GestionCuenta
import com.cocinacapital.app.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import com.cocinacapital.app.data.local.ArchivoFoto
import com.cocinacapital.app.data.local.FotoPerfilArchivos
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del Perfil. Une tres fuentes: usuario (Auth), preferencias y favoritos (Room)
 * y las categorías disponibles (las mismas del Home).
 */
class PerfilViewModel(
    private val auth: PerfilSesion,
    private val preferencias: PreferenciasRepository,
    private val favoritos: FavoritosRepository,
    private val catalogo: HomeRepository,
    private val archivosFoto: FotoPerfilArchivos,
    private val billetera: BilleteraRepository? = null,
    private val cuenta: GestionCuenta? = null
) : ViewModel() {

    /** Avisos breves (historial borrado, errores de red) que la pantalla muestra una vez. */
    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    /** Mientras se elimina la cuenta se bloquean los botones para no repetir la acción. */
    private val _eliminandoCuenta = MutableStateFlow(false)
    val eliminandoCuenta: StateFlow<Boolean> = _eliminandoCuenta.asStateFlow()

    /** `null` = cargando; `Result.failure` = error. */
    private val categorias = MutableStateFlow<Result<List<Categoria>>?>(null)

    /** Canjeados de la billetera; un fallo no impide mostrar el perfil. */
    private val canjeados = MutableStateFlow(0)

    private val _foto = MutableStateFlow(FotoPerfilUiState())
    val foto: StateFlow<FotoPerfilUiState> = _foto.asStateFlow()

    /** Archivo entregado a la cámara; se guarda o se borra según el resultado. */
    private var archivoCamara: ArchivoFoto? = null

    val uiState: StateFlow<PerfilUiState> = combine(
        auth.usuario, preferencias.observar(), favoritos.observar(), categorias, canjeados
    ) { usuario, prefs, favs, cats, canjes ->
        when {
            cats == null -> PerfilUiState.Loading
            cats.isFailure -> PerfilUiState.Error(com.cocinacapital.app.R.string.perfil_error_cargar)
            else -> PerfilUiState.Success(usuario, prefs, cats.getOrThrow(), favs, canjes)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilUiState.Loading)

    init { cargarCategorias() }

    fun onReintentar() = cargarCategorias()

    /** Al volver a Perfil: pudo canjear un beneficio en un local. */
    fun actualizarCanjeados() {
        val repo = billetera ?: return
        viewModelScope.launch {
            try {
                canjeados.value = repo.misBeneficios().count { it.estado == EstadoBeneficio.CANJEADO }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Se conserva el último valor.
            }
        }
    }

    private fun cargarCategorias() {
        viewModelScope.launch {
            categorias.value = null
            categorias.value = try {
                Result.success(catalogo.getCategorias())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ---- Mis datos ----
    fun onGuardarNombre(nombre: String) {
        val limpio = nombre.trim()
        if (limpio.length >= 2) viewModelScope.launch { auth.actualizarNombre(limpio) }
    }

    // ---- Foto de perfil (T-23) ----
    fun onCambiarFoto() = _foto.update { it.copy(dialogo = DialogoFoto.ELEGIR_ORIGEN) }
    fun onCerrarDialogoFoto() = _foto.update { it.copy(dialogo = null) }
    fun onAccionFotoRealizada() = _foto.update { it.copy(accion = null) }

    fun onElegirGaleria() = _foto.update { FotoPerfilUiState(accion = AccionFoto.AbrirGaleria) }

    /** Si falta el permiso, primero se explica para qué se usa la cámara. */
    fun onElegirCamara(permisoConcedido: Boolean) {
        if (permisoConcedido) abrirCamara()
        else _foto.update { FotoPerfilUiState(dialogo = DialogoFoto.EXPLICAR_PERMISO) }
    }

    fun onAceptarExplicacionPermiso() = _foto.update { FotoPerfilUiState(accion = AccionFoto.PedirPermisoCamara) }

    fun onResultadoPermisoCamara(concedido: Boolean) {
        if (concedido) abrirCamara()
        else _foto.update { FotoPerfilUiState(dialogo = DialogoFoto.PERMISO_DENEGADO) }
    }

    fun onAbrirAjustes() = _foto.update { FotoPerfilUiState(accion = AccionFoto.AbrirAjustes) }

    fun onFotoTomada(exito: Boolean) {
        val archivo = archivoCamara ?: return
        archivoCamara = null
        if (exito) guardarFoto { archivo.ruta } else archivosFoto.eliminar(archivo.ruta)
    }

    /** `null` = el usuario cerró el selector sin elegir. */
    fun onFotoElegida(uri: String?) {
        if (uri != null) guardarFoto { archivosFoto.copiarDesde(uri) }
    }

    fun onQuitarFoto() {
        _foto.update { it.copy(dialogo = null) }
        viewModelScope.launch { preferencias.setFotoPerfil(null)?.let(archivosFoto::eliminar) }
    }

    private fun abrirCamara() {
        _foto.value = try {
            val archivo = archivosFoto.nuevoArchivoCamara().also { archivoCamara = it }
            FotoPerfilUiState(accion = AccionFoto.AbrirCamara(archivo.uri))
        } catch (e: Exception) {
            FotoPerfilUiState(dialogo = DialogoFoto.ERROR_GUARDAR)
        }
    }

    /** Guarda la nueva ruta y borra el archivo de la foto anterior. */
    private fun guardarFoto(obtenerRuta: suspend () -> String) {
        viewModelScope.launch {
            try {
                val ruta = obtenerRuta()
                val anterior = preferencias.setFotoPerfil(ruta)
                if (anterior != null && anterior != ruta) archivosFoto.eliminar(anterior)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _foto.update { it.copy(dialogo = DialogoFoto.ERROR_GUARDAR) }
            }
        }
    }

    // ---- Preferencias ----
    fun onCategoriaClick(id: String) = viewModelScope.launch { preferencias.alternarCategoria(id) }.let {}
    fun onRangoPrecio(rango: RangoPrecio) = viewModelScope.launch { preferencias.setRangoPrecio(rango) }.let {}

    // ---- Favoritos ----
    fun onQuitarFavorito(favorito: Favorito) = viewModelScope.launch { favoritos.eliminar(favorito) }.let {}

    // ---- Notificaciones ----
    fun onNotificaciones(activas: Boolean) = viewModelScope.launch { preferencias.setNotificaciones(activas) }.let {}
    fun onSoloCercanas(v: Boolean) = viewModelScope.launch { preferencias.setSoloCercanas(v) }.let {}
    fun onSoloCategoriasFavoritas(v: Boolean) = viewModelScope.launch { preferencias.setSoloCategoriasFavoritas(v) }.let {}

    // ---- Privacidad ----
    fun onConsentimiento(acepta: Boolean) = viewModelScope.launch { preferencias.setConsentimiento(acepta) }.let {}

    /** T-07: borra en Supabase las búsquedas, vistas y canjes registrados (HU-20). */
    fun onBorrarHistorial() {
        val gestion = cuenta ?: return
        viewModelScope.launch {
            val mensaje = try {
                gestion.borrarHistorial()
                R.string.perfil_historial_borrado
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                R.string.perfil_error_borrar_historial
            }
            _mensajes.trySend(mensaje)
        }
    }

    /** T-07: al terminar, la sesión se cierra y la app vuelve al Login. */
    fun onEliminarCuenta() {
        val gestion = cuenta ?: return
        if (_eliminandoCuenta.value) return
        _eliminandoCuenta.value = true
        viewModelScope.launch {
            try {
                gestion.eliminarCuenta()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // La cuenta sigue intacta: se puede volver a intentar.
                _mensajes.trySend(R.string.perfil_error_eliminar_cuenta)
            } finally {
                _eliminandoCuenta.value = false
            }
        }
    }

    // ---- Aplicación ----
    fun onTema(tema: TemaApp) = viewModelScope.launch { preferencias.setTema(tema) }.let {}
    fun onCerrarSesion() = viewModelScope.launch { auth.cerrarSesion() }.let {}

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PerfilViewModel(
                    AppContainer.perfilSesion,
                    AppContainer.preferenciasRepository,
                    AppContainer.favoritosRepository,
                    AppContainer.homeRepository,
                    AppContainer.fotoPerfilArchivos,
                    AppContainer.billeteraRepository,
                    AppContainer.gestionCuenta
                )
            }
        }
    }
}
