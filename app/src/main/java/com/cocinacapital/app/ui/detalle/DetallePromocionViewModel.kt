package com.cocinacapital.app.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.vigenciaParaDetalle
import com.cocinacapital.app.data.repository.DetallePromocionRepository
import com.cocinacapital.app.di.AppContainer
import java.time.Clock
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class DetallePromocionViewModel(
    private val promocionId: String,
    private val repository: DetallePromocionRepository,
    private val reloj: Clock = Clock.systemUTC()
) : ViewModel() {
    private val mutable = MutableStateFlow<DetallePromocionUiState>(DetallePromocionUiState.Loading)
    val uiState: StateFlow<DetallePromocionUiState> = mutable.asStateFlow()
    private var carga: Job? = null
    private var generacion = 0L

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    /**
     * Tras obtener un beneficio (HU-13) los cupos cambiaron: se recarga sin ocultar la pantalla y,
     * si falla, se conserva lo que ya se veía.
     */
    fun actualizarCupos() = cargar(silenciosa = true)

    /** Se actualiza mientras la pantalla está visible, sin repetir consultas de red. */
    fun actualizarVigencia() {
        mutable.update { actual ->
            if (actual is DetallePromocionUiState.Success) {
                actual.copy(vigencia = actual.promocion.vigenciaParaDetalle(reloj.instant(), actual.cuposConfigurados, actual.horarioConfigurado))
            } else actual
        }
    }

    private fun cargar(silenciosa: Boolean = false) {
        val version = ++generacion
        carga?.cancel()
        if (promocionId.isBlank()) {
            mutable.value = DetallePromocionUiState.NoEncontrada
            return
        }
        val visible = mutable.value is DetallePromocionUiState.Success
        if (!silenciosa || !visible) mutable.value = DetallePromocionUiState.Loading
        carga = viewModelScope.launch {
            val resultado = try {
                val detalle = consultar { repository.getPromocion(promocionId) }
                if (detalle == null) {
                    DetallePromocionUiState.NoEncontrada
                } else {
                    val promocion = detalle.promocion
                    var errorLocal = false
                    val local = try {
                        consultar { repository.getLocal(promocion.localId) }?.takeIf {
                            it.id == promocion.localId
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        errorLocal = true
                        null
                    }
                    DetallePromocionUiState.Success(
                        promocion = promocion,
                        local = local,
                        vigencia = promocion.vigenciaParaDetalle(reloj.instant(), detalle.cuposConfigurados, detalle.horarioConfigurado),
                        errorLocal = errorLocal,
                        cuposConfigurados = detalle.cuposConfigurados,
                        horarioConfigurado = detalle.horarioConfigurado
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DetallePromocionUiState.Error
            }
            val conservar = silenciosa && resultado is DetallePromocionUiState.Error &&
                mutable.value is DetallePromocionUiState.Success
            if (version == generacion && !conservar) mutable.value = resultado
        }
    }

    private suspend fun <T> consultar(operacion: suspend () -> T): T = try {
        withTimeout(TIEMPO_LIMITE_CONSULTA_MS) { operacion() }
    } catch (error: TimeoutCancellationException) {
        // La espera agotada se puede reintentar; salir de la pantalla sigue cancelando la carga.
        currentCoroutineContext().ensureActive()
        throw TimeoutException("Se agotó el tiempo de espera de la consulta").apply { initCause(error) }
    }

    companion object {
        private const val TIEMPO_LIMITE_CONSULTA_MS = 15_000L

        fun factory(promocionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetallePromocionViewModel(promocionId, AppContainer.detallePromocionRepository)
            }
        }
    }
}
