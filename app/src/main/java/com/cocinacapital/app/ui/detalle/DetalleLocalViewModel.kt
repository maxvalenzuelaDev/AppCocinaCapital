package com.cocinacapital.app.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.repository.DetalleLocalRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel del detalle de un local (RF-05): carga local, menú destacado y promociones en paralelo. */
class DetalleLocalViewModel(
    private val localId: String,
    private val repository: DetalleLocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetalleLocalUiState>(DetalleLocalUiState.Loading)
    val uiState: StateFlow<DetalleLocalUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = DetalleLocalUiState.Loading
            _uiState.value = try {
                coroutineScope {
                    val local = async { repository.getLocal(localId) }
                    val platos = async { repository.getPlatosDestacados(localId) }
                    val promociones = async { repository.getPromocionesVigentes(localId) }
                    val localOk = local.await()
                    if (localOk == null) {
                        DetalleLocalUiState.NoEncontrado
                    } else {
                        DetalleLocalUiState.Success(
                            local = localOk,
                            platosDestacados = platos.await(),
                            // La tarjeta de promoción muestra el nombre del local.
                            promociones = promociones.await().map {
                                it.copy(localNombre = localOk.nombre, localCategoriaId = localOk.categoriaId)
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e // nunca tragarse la cancelación de corrutinas
            } catch (e: Exception) {
                DetalleLocalUiState.Error
            }
        }
    }

    companion object {
        fun factory(localId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetalleLocalViewModel(localId, AppContainer.detalleLocalRepository) }
        }
    }
}
