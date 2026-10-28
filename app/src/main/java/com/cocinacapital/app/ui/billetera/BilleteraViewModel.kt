package com.cocinacapital.app.ui.billetera

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.repository.BilleteraRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * Billetera del consumidor: sus beneficios vigentes con el código y QR para el local (HU-14), y el
 * historial de canjeados y vencidos (HU-16). Quitar uno libera su cupo para otra persona.
 */
class BilleteraViewModel(
    private val billetera: BilleteraRepository,
    private val reloj: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow<BilleteraUiState>(BilleteraUiState.Loading)
    val uiState: StateFlow<BilleteraUiState> = _uiState.asStateFlow()

    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    val ahora: Instant get() = reloj.instant()

    private var carga: Job? = null

    init {
        cargar()
    }

    /**
     * Vuelve a consultar Supabase (al entrar a la pestaña, por ejemplo tras un canje en el local).
     * Con la lista ya visible no se muestra la carga: se reemplaza al llegar la respuesta.
     */
    fun cargar() {
        carga?.cancel()
        if (_uiState.value !is BilleteraUiState.Success) _uiState.value = BilleteraUiState.Loading
        carga = viewModelScope.launch {
            try {
                val beneficios = billetera.misBeneficios()
                _uiState.update { actual ->
                    if (actual is BilleteraUiState.Success) actual.conBeneficios(beneficios)
                    else BilleteraUiState.Success(beneficios)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Si ya había una lista se conserva; solo se avisa.
                if (_uiState.value is BilleteraUiState.Success) avisar(R.string.billetera_error_cargar)
                else _uiState.value = BilleteraUiState.Error(R.string.billetera_error_cargar)
            }
        }
    }

    /** Al volver a la pestaña; la primera carga ya la hace `init`. */
    fun refrescar() {
        if (_uiState.value is BilleteraUiState.Success) cargar()
    }

    fun onMostrarCodigo(beneficio: BeneficioUsuario) = actualizar { it.copy(mostrando = beneficio) }

    fun onCerrarCodigo() = actualizar { it.copy(mostrando = null) }

    fun onPedirQuitar(beneficio: BeneficioUsuario) = actualizar { it.copy(porQuitar = beneficio) }

    fun onCancelarQuitar() = actualizar { it.copy(porQuitar = null) }

    fun onConfirmarQuitar() {
        val actual = _uiState.value as? BilleteraUiState.Success ?: return
        val beneficio = actual.porQuitar ?: return
        if (actual.quitandoId != null) return
        actualizar { it.copy(porQuitar = null, quitandoId = beneficio.id) }
        viewModelScope.launch {
            try {
                billetera.liberar(beneficio.id)
                actualizar { estado ->
                    estado.copy(beneficios = estado.beneficios.filterNot { it.id == beneficio.id }, quitandoId = null)
                }
                avisar(R.string.billetera_quitado)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actualizar { it.copy(quitandoId = null) }
                avisar(R.string.billetera_error_quitar)
                // Puede que se haya canjeado mientras tanto: se trae el estado real.
                cargar()
            }
        }
    }

    /** Lista nueva conservando lo abierto solo si ese beneficio sigue en ella. */
    private fun BilleteraUiState.Success.conBeneficios(nuevos: List<BeneficioUsuario>) = copy(
        beneficios = nuevos,
        mostrando = mostrando?.let { m -> nuevos.firstOrNull { it.id == m.id } },
        porQuitar = porQuitar?.let { p -> nuevos.firstOrNull { it.id == p.id } }
    )

    private fun actualizar(cambio: (BilleteraUiState.Success) -> BilleteraUiState.Success) =
        _uiState.update { if (it is BilleteraUiState.Success) cambio(it) else it }

    private fun avisar(@StringRes mensaje: Int) { _mensajes.trySend(mensaje) }

    companion object {
        val Factory = viewModelFactory {
            initializer { BilleteraViewModel(AppContainer.billeteraRepository) }
        }
    }
}
