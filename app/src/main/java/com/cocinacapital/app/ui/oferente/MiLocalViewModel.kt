package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
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
class MiLocalViewModel(private val repository: MiLocalRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MiLocalUiState>(MiLocalUiState.Loading)
    val uiState: StateFlow<MiLocalUiState> = _uiState.asStateFlow()

    /** Avisos de una sola vez (guardado, error al guardar) para mostrar en un snackbar. */
    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    init { cargar() }

    fun onReintentar() = cargar()

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
            initializer { MiLocalViewModel(AppContainer.miLocalRepository) }
        }
    }
}
