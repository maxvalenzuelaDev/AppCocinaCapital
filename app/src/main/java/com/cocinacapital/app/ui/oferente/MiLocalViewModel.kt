package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.location.EstadoUbicacion
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.data.repository.MiLocalRepository
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
 * ViewModel de "Mi local" (HU-25): carga el local del oferente (o un formulario vacío si aún no
 * tiene uno), valida los datos y los guarda en Supabase. Los cambios se ven en la app del consumidor
 * la próxima vez que cargue el catálogo.
 */
class MiLocalViewModel(
    private val repository: MiLocalRepository,
    private val ubicacion: UbicacionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MiLocalUiState>(MiLocalUiState.Loading)
    val uiState: StateFlow<MiLocalUiState> = _uiState.asStateFlow()

    /** Avisos de una sola vez (guardado, error al guardar) para mostrar en un snackbar. */
    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    /** Marca de tiempo de la posición que había al pulsar el botón; `null` si no se espera ninguna. */
    private var esperandoUbicacionDesde: Long? = null

    init {
        cargar()
        viewModelScope.launch { ubicacion.estado.collect(::alCambiarUbicacion) }
    }

    fun onReintentar() = cargar()

    /**
     * Se llama al pulsar "Usar mi ubicación actual", antes de pedir la posición (el permiso y los
     * avisos de GPS los maneja la acción compartida de HU-17). La próxima posición nueva se usa como
     * ubicación del local.
     */
    fun onUsarUbicacion() {
        esperandoUbicacionDesde = ubicacion.estado.value.posicion?.timestampMillis ?: Long.MIN_VALUE
        consultaIniciada = false
    }

    /** La consulta pedida ya empezó: su resultado sirve aunque repita la posición anterior (caché). */
    private var consultaIniciada = false

    private fun alCambiarUbicacion(estado: EstadoUbicacion) {
        val desde = esperandoUbicacionDesde ?: return
        if (estado.cargando) { consultaIniciada = true; return }
        if (estado.problema != null) { esperandoUbicacionDesde = null; return }
        val posicion = estado.posicion ?: return
        if (!consultaIniciada && posicion.timestampMillis <= desde) return
        esperandoUbicacionDesde = null
        actualizar {
            it.copy(
                formulario = it.formulario.copy(latitud = posicion.latitude, longitud = posicion.longitude),
                precisionMetros = posicion.accuracyMeters
            )
        }
    }

    fun onCambio(cambio: FormularioLocal.() -> FormularioLocal) = actualizar {
        // Al corregir un campo se ocultan los errores; vuelven a validarse al guardar.
        it.copy(formulario = it.formulario.cambio(), errores = ErroresLocal())
    }

    fun onGuardar() {
        val actual = _uiState.value as? MiLocalUiState.Success ?: return
        if (actual.guardando) return
        val (datos, errores) = validarLocal(actual.formulario, actual.categorias)
        if (datos == null) {
            actualizar { it.copy(errores = errores) }
            return
        }
        actualizar { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val guardado = repository.guardar(actual.localId, datos)
                actualizar {
                    it.copy(localId = guardado.id, formulario = guardado.aFormulario(), guardando = false)
                }
                avisar(if (actual.localId == null) R.string.mi_local_creado else R.string.mi_local_guardado)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actualizar { it.copy(guardando = false) }
                avisar(R.string.mi_local_error_guardar)
            }
        }
    }

    private fun cargar() {
        _uiState.value = MiLocalUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                coroutineScope {
                    val categorias = async { repository.getCategorias() }
                    val local = async { repository.obtenerMiLocal() }
                    val miLocal = local.await()
                    MiLocalUiState.Success(
                        categorias = categorias.await(),
                        formulario = miLocal?.aFormulario() ?: FormularioLocal(),
                        localId = miLocal?.id
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MiLocalUiState.Error(R.string.mi_local_error_cargar)
            }
        }
    }

    private fun actualizar(cambio: (MiLocalUiState.Success) -> MiLocalUiState.Success) =
        _uiState.update { if (it is MiLocalUiState.Success) cambio(it) else it }

    private fun avisar(@StringRes mensaje: Int) { _mensajes.trySend(mensaje) }

    companion object {
        val Factory = viewModelFactory {
            initializer { MiLocalViewModel(AppContainer.miLocalRepository, AppContainer.ubicacionRepository) }
        }
    }
}
