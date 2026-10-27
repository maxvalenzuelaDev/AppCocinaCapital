package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.repository.CatalogoRepository
import com.cocinacapital.app.data.repository.MiLocalRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del catálogo del oferente (HU-26): lista los platos de su local y permite agregarlos,
 * editarlos y eliminarlos. Los destacados aparecen en el detalle del local que ve el consumidor.
 */
class CatalogoViewModel(
    private val miLocal: MiLocalRepository,
    private val catalogo: CatalogoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogoUiState>(CatalogoUiState.Loading)
    val uiState: StateFlow<CatalogoUiState> = _uiState.asStateFlow()

    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    init { cargar() }

    fun onReintentar() = cargar()

    /** Al volver a la pestaña: si antes no había local (o falló la carga), se consulta de nuevo. */
    fun onMostrar() {
        val estado = _uiState.value
        if (estado is CatalogoUiState.SinLocal || estado is CatalogoUiState.Error) cargar()
    }

    fun onAgregar() = actualizar { it.copy(editor = EditorPlato()) }

    fun onEditar(plato: Plato) = actualizar { it.copy(editor = plato.aEditor()) }

    fun onCambio(cambio: EditorPlato.() -> EditorPlato) = actualizar { estado ->
        // Al corregir un campo se ocultan los errores; vuelven a validarse al guardar.
        estado.copy(editor = estado.editor?.cambio()?.copy(errorNombre = null, errorDescripcion = null, errorPrecio = null))
    }

    fun onCerrarEditor() = actualizar { if (it.guardando) it else it.copy(editor = null) }

    fun onGuardar() {
        val actual = _uiState.value as? CatalogoUiState.Success ?: return
        val editor = actual.editor ?: return
        if (actual.guardando) return
        val (datos, conErrores) = validarPlato(editor)
        if (datos == null) {
            actualizar { it.copy(editor = conErrores) }
            return
        }
        actualizar { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val guardado = if (editor.platoId == null) catalogo.crear(actual.localId, datos)
                    else catalogo.actualizar(editor.platoId, datos)
                actualizar { estado ->
                    val platos = estado.platos.filterNot { it.id == guardado.id } + guardado
                    estado.copy(platos = platos.sortedBy { it.nombre.lowercase() }, editor = null, guardando = false)
                }
                avisar(if (editor.platoId == null) R.string.catalogo_creado else R.string.catalogo_guardado)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // El diálogo queda abierto con lo escrito para reintentar.
                actualizar { it.copy(guardando = false) }
                avisar(R.string.catalogo_error_guardar)
            }
        }
    }

    fun onPedirEliminar(plato: Plato) = actualizar { it.copy(porEliminar = plato) }

    fun onCancelarEliminar() = actualizar { if (it.guardando) it else it.copy(porEliminar = null) }

    fun onConfirmarEliminar() {
        val actual = _uiState.value as? CatalogoUiState.Success ?: return
        val plato = actual.porEliminar ?: return
        if (actual.guardando) return
        actualizar { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                catalogo.eliminar(plato.id)
                actualizar { estado ->
                    estado.copy(platos = estado.platos.filterNot { it.id == plato.id }, porEliminar = null, guardando = false)
                }
                avisar(R.string.catalogo_eliminado)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actualizar { it.copy(porEliminar = null, guardando = false) }
                avisar(R.string.catalogo_error_eliminar)
            }
        }
    }

    private fun cargar() {
        _uiState.value = CatalogoUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val local = miLocal.obtenerMiLocal()
                if (local == null) CatalogoUiState.SinLocal
                else CatalogoUiState.Success(local.id, catalogo.listar(local.id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CatalogoUiState.Error(R.string.catalogo_error_cargar)
            }
        }
    }

    private fun actualizar(cambio: (CatalogoUiState.Success) -> CatalogoUiState.Success) =
        _uiState.update { if (it is CatalogoUiState.Success) cambio(it) else it }

    private fun avisar(@StringRes mensaje: Int) { _mensajes.trySend(mensaje) }

    companion object {
        val Factory = viewModelFactory {
            initializer { CatalogoViewModel(AppContainer.miLocalRepository, AppContainer.catalogoRepository) }
        }
    }
}
