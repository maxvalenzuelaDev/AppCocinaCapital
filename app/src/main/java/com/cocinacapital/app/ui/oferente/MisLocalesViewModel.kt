package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.location.EstadoUbicacion
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.repository.MisLocalesRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de "Mis locales" (HU-25): lista las tiendas del oferente, abre el formulario para crear
 * o editar una y la guarda en Supabase. También guarda qué local está elegido para Catálogo y
 * Promociones, por eso una sola instancia por cuenta se comparte entre las pestañas.
 * Los cambios se ven en la app del consumidor la próxima vez que cargue el catálogo.
 */
class MisLocalesViewModel(
    private val repository: MisLocalesRepository,
    private val ubicacion: UbicacionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MisLocalesUiState>(MisLocalesUiState.Loading)
    val uiState: StateFlow<MisLocalesUiState> = _uiState.asStateFlow()

    /** Avisos de una sola vez (guardado, error al guardar) para mostrar en un snackbar. */
    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    /** Marca de tiempo de la posición que había al pulsar el botón; `null` si no se espera ninguna. */
    private var esperandoUbicacionDesde: Long? = null

    /** La consulta pedida ya empezó: su resultado sirve aunque repita la posición anterior (caché). */
    private var consultaIniciada = false

    init {
        cargar()
        viewModelScope.launch { ubicacion.estado.collect(::alCambiarUbicacion) }
    }

    fun onReintentar() = cargar()

    /** Local con el que trabajan Catálogo y Promociones. */
    fun onSeleccionar(localId: String) = actualizar { estado ->
        if (estado.locales.any { it.id == localId }) estado.copy(seleccionadoId = localId) else estado
    }

    fun onNuevo() = actualizar { it.copy(editor = EditorLocal()) }

    fun onEditar(local: LocalGastronomico) = actualizar {
        it.copy(editor = EditorLocal(localId = local.id, formulario = local.aFormulario()))
    }

    fun onCerrarEditor() = actualizar { if (it.editor?.guardando == true) it else it.copy(editor = null) }

    /**
     * Se llama al pulsar "Usar mi ubicación actual", antes de pedir la posición (el permiso y los
     * avisos de GPS los maneja la acción compartida de HU-17). La próxima posición nueva se usa como
     * ubicación del local que se está editando.
     */
    fun onUsarUbicacion() {
        esperandoUbicacionDesde = ubicacion.estado.value.posicion?.timestampMillis ?: Long.MIN_VALUE
        consultaIniciada = false
    }

    private fun alCambiarUbicacion(estado: EstadoUbicacion) {
        val desde = esperandoUbicacionDesde ?: return
        if (estado.cargando) { consultaIniciada = true; return }
        if (estado.problema != null) { esperandoUbicacionDesde = null; return }
        val posicion = estado.posicion ?: return
        if (!consultaIniciada && posicion.timestampMillis <= desde) return
        esperandoUbicacionDesde = null
        actualizarEditor {
            it.copy(
                formulario = it.formulario.copy(latitud = posicion.latitude, longitud = posicion.longitude),
                precisionMetros = posicion.accuracyMeters
            )
        }
    }

    fun onCambio(cambio: FormularioLocal.() -> FormularioLocal) = actualizarEditor {
        // Al corregir un campo se ocultan los errores; vuelven a validarse al guardar.
        it.copy(formulario = it.formulario.cambio(), errores = ErroresLocal())
    }

    fun onGuardar() {
        val actual = _uiState.value as? MisLocalesUiState.Success ?: return
        val editor = actual.editor ?: return
        if (editor.guardando) return
        val (datos, errores) = validarLocal(editor.formulario, actual.categorias)
        if (datos == null) {
            actualizarEditor { it.copy(errores = errores) }
            return
        }
        actualizarEditor { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val guardado = repository.guardar(editor.localId, datos)
                actualizar { estado ->
                    val locales = (estado.locales.filterNot { it.id == guardado.id } + guardado).sortedBy { it.nombre.lowercase() }
                    // Un local recién creado queda elegido para seguir con su catálogo y promociones.
                    val seleccionado = if (editor.localId == null) guardado.id else estado.seleccionadoId ?: guardado.id
                    estado.copy(locales = locales, seleccionadoId = seleccionado, editor = null)
                }
                avisar(if (editor.localId == null) R.string.mi_local_creado else R.string.mi_local_guardado)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actualizarEditor { it.copy(guardando = false) }
                avisar(R.string.mi_local_error_guardar)
            }
        }
    }

    private fun cargar() {
        _uiState.value = MisLocalesUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                coroutineScope {
                    val categorias = async { repository.getCategorias() }
                    val locales = repository.listar()
                    MisLocalesUiState.Success(
                        locales = locales,
                        categorias = categorias.await(),
                        seleccionadoId = locales.firstOrNull()?.id
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MisLocalesUiState.Error(R.string.mi_local_error_cargar)
            }
        }
    }

    private fun actualizar(cambio: (MisLocalesUiState.Success) -> MisLocalesUiState.Success) =
        _uiState.update { if (it is MisLocalesUiState.Success) cambio(it) else it }

    private fun actualizarEditor(cambio: (EditorLocal) -> EditorLocal) =
        actualizar { estado -> estado.editor?.let { estado.copy(editor = cambio(it)) } ?: estado }

    private fun avisar(@StringRes mensaje: Int) { _mensajes.trySend(mensaje) }

    companion object {
        val Factory = viewModelFactory {
            initializer { MisLocalesViewModel(AppContainer.misLocalesRepository, AppContainer.ubicacionRepository) }
        }
    }
}
