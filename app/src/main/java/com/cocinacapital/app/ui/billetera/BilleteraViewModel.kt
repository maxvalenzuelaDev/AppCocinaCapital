package com.cocinacapital.app.ui.billetera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocinacapital.app.data.repository.BilleteraRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la billetera de Cocina Capital.
 *
 * Obtiene las promociones guardadas desde Supabase,
 * incluyendo su información real.
 */
class BilleteraViewModel : ViewModel() {

    private val repository = BilleteraRepository()

    private val _uiState = MutableStateFlow(
        BilleteraUiState(cargando = true)
    )

    val uiState: StateFlow<BilleteraUiState> =
        _uiState.asStateFlow()

    init {
        cargarBeneficios()
    }

    /**
     * Consulta los beneficios y obtiene la información
     * completa de las promociones guardadas.
     */
    fun cargarBeneficios() {
        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    cargando = true,
                    mensajeError = null
                )
            }

            try {
                // Consulta los beneficios del consumidor.
                val beneficios = repository.obtenerBeneficios()

                // Consulta los datos completos de las promociones.
                val promociones = repository.obtenerPromocionesGuardadas()

                _uiState.update {
                    it.copy(
                        cargando = false,
                        beneficios = beneficios,
                        promocionesGuardadas = promociones
                    )
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                android.util.Log.e(
                    "CocinaCapitalBilletera",
                    "Error al cargar la billetera",
                    e
                )

                _uiState.update {
                    it.copy(
                        cargando = false,
                        mensajeError = "No se pudieron cargar tus promociones. Inténtalo nuevamente."
                    )
                }
            }
        }
    }

    /**
     * Guarda una promoción en Supabase y actualiza
     * la información de la billetera.
     */
    fun guardarBeneficio(promocionId: String) {
        if (_uiState.value.procesando) return

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    procesando = true,
                    mensajeError = null
                )
            }

            try {
                repository.guardarBeneficio(promocionId)

                // Vuelve a consultar Supabase.
                cargarBeneficios()

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                android.util.Log.e(
                    "CocinaCapitalBilletera",
                    "Error al guardar beneficio",
                    e
                )

                _uiState.update {
                    it.copy(
                        mensajeError = "No se pudo guardar la promoción."
                    )
                }

            } finally {
                _uiState.update {
                    it.copy(procesando = false)
                }
            }
        }
    }

    /**
     * Elimina una promoción guardada y actualiza
     * la información de la billetera.
     */
    fun eliminarBeneficio(promocionId: String) {
        if (_uiState.value.procesando) return

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    procesando = true,
                    mensajeError = null
                )
            }

            try {
                repository.eliminarBeneficio(promocionId)

                // Actualiza la lista después de eliminar.
                cargarBeneficios()

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                android.util.Log.e(
                    "CocinaCapitalBilletera",
                    "Error al eliminar beneficio",
                    e
                )

                _uiState.update {
                    it.copy(
                        mensajeError = "No se pudo eliminar la promoción."
                    )
                }

            } finally {
                _uiState.update {
                    it.copy(procesando = false)
                }
            }
        }
    }
}
