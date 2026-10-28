package com.cocinacapital.app.ui.billetera

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.repository.BilleteraException
import com.cocinacapital.app.data.repository.BilleteraRepository
import com.cocinacapital.app.data.repository.MotivoBilletera
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
 * Obtener el beneficio de una promoción (HU-13). Supabase valida vigencia y cupos y descuenta uno;
 * si la persona ya lo tenía, devuelve el mismo código sin gastar otro cupo.
 */
class BeneficioPromocionViewModel(
    private val promocionId: String,
    private val billetera: BilleteraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BeneficioPromocionUiState())
    val uiState: StateFlow<BeneficioPromocionUiState> = _uiState.asStateFlow()

    /** Se emite al obtener un beneficio: los cupos de la promoción cambiaron. */
    private val _obtenidos = Channel<Unit>(Channel.CONFLATED)
    val obtenidos: Flow<Unit> = _obtenidos.receiveAsFlow()

    init {
        revisar()
    }

    /** Al volver al detalle: pudo quitarlo o canjearlo desde la billetera. */
    fun revisar() {
        if (_uiState.value.obteniendo) return
        viewModelScope.launch {
            try {
                val beneficio = billetera.beneficioDe(promocionId)
                _uiState.update { it.copy(cargando = false, beneficio = beneficio, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // El botón sigue disponible: si ya lo tenía, Supabase devuelve el mismo beneficio.
                _uiState.update { it.copy(cargando = false, error = R.string.beneficio_error_estado) }
            }
        }
    }

    fun onObtener() {
        val actual = _uiState.value
        if (actual.obteniendo || actual.beneficio != null) return
        _uiState.update { it.copy(obteniendo = true, error = null) }
        viewModelScope.launch {
            try {
                val beneficio = billetera.adquirir(promocionId)
                _uiState.update { it.copy(obteniendo = false, beneficio = beneficio, mostrarCodigo = true, recienObtenido = true) }
                _obtenidos.trySend(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: BilleteraException) {
                _uiState.update { it.copy(obteniendo = false, error = mensajeDe(error.motivo)) }
                // Sin cupos o vencida: el detalle debe mostrarlo también.
                _obtenidos.trySend(Unit)
            } catch (_: Exception) {
                _uiState.update { it.copy(obteniendo = false, error = R.string.beneficio_error_obtener) }
            }
        }
    }

    fun onVerCodigo() = _uiState.update { if (it.beneficio != null) it.copy(mostrarCodigo = true, recienObtenido = false) else it }

    fun onCerrarCodigo() = _uiState.update { it.copy(mostrarCodigo = false) }

    companion object {
        @StringRes
        fun mensajeDe(motivo: MotivoBilletera): Int = when (motivo) {
            MotivoBilletera.SIN_CUPOS -> R.string.beneficio_error_sin_cupos
            MotivoBilletera.PROMOCION_VENCIDA -> R.string.beneficio_error_vencida
            MotivoBilletera.PROMOCION_NO_INICIADA -> R.string.beneficio_error_no_iniciada
            MotivoBilletera.PROMOCION_NO_DISPONIBLE -> R.string.beneficio_error_no_disponible
            MotivoBilletera.SIN_SESION -> R.string.beneficio_error_sin_sesion
            else -> R.string.beneficio_error_obtener
        }

        fun factory(promocionId: String) = viewModelFactory {
            initializer { BeneficioPromocionViewModel(promocionId, AppContainer.billeteraRepository) }
        }
    }
}
