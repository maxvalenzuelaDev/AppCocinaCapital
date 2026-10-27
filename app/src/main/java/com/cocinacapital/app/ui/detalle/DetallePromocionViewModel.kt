package com.cocinacapital.app.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.vigenciaEn
import com.cocinacapital.app.data.repository.DetallePromocionRepository
import com.cocinacapital.app.di.AppContainer
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    /** Se actualiza mientras la pantalla está visible, sin repetir consultas de red. */
    fun actualizarVigencia() {
        mutable.update { actual ->
            if (actual is DetallePromocionUiState.Success) {
                actual.copy(vigencia = actual.promocion.vigenciaEn(reloj.instant()))
            } else actual
        }
    }

    private fun cargar() {
        val version = ++generacion
        carga?.cancel()
        if (promocionId.isBlank()) {
            mutable.value = DetallePromocionUiState.NoEncontrada
            return
        }
        mutable.value = DetallePromocionUiState.Loading
        carga = viewModelScope.launch {
            val resultado = try {
                val detalle = repository.getPromocion(promocionId)
                if (detalle == null) {
                    DetallePromocionUiState.NoEncontrada
                } else {
                    val promocion = detalle.promocion
                    var errorLocal = false
                    val local = try {
                        repository.getLocal(promocion.localId)?.takeIf {
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
                        vigencia = promocion.vigenciaEn(reloj.instant()),
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
            if (version == generacion) mutable.value = resultado
        }
    }

    companion object {
        fun factory(promocionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetallePromocionViewModel(promocionId, AppContainer.detallePromocionRepository)
            }
        }
    }
}
