package com.cocinacapital.app.ui.experiencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.Experiencia
import com.cocinacapital.app.data.repository.ExperienciasRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Misma carga cancelable para listado y detalle, cada uno con su propia instancia por ruta. */
abstract class CargaExperienciasViewModel<T>(private val consultar: suspend () -> T) : ViewModel() {
    private val mutable = MutableStateFlow<ExperienciasUiState<T>>(ExperienciasUiState.Loading)
    val uiState = mutable.asStateFlow()
    private var carga: Job? = null
    private var version = 0L

    init { onReintentar() }

    fun onReintentar() {
        val actual = ++version
        carga?.cancel()
        mutable.value = ExperienciasUiState.Loading
        carga = viewModelScope.launch {
            val resultado = try {
                ExperienciasUiState.Success(withTimeout(15_000) { consultar() })
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                ExperienciasUiState.Error
            } catch (error: CancellationException) {
                // Si se canceló la pantalla, se propaga; una cancelación interna de la consulta es un error
                // recuperable y debe mostrar "Reintentar" en vez de quedar cargando para siempre.
                currentCoroutineContext().ensureActive()
                ExperienciasUiState.Error
            } catch (_: Exception) {
                ExperienciasUiState.Error
            }
            if (actual == version) mutable.value = resultado
        }
    }
}

class ExperienciasViewModel(repository: ExperienciasRepository) :
    CargaExperienciasViewModel<List<Experiencia>>(repository::listar) {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ExperienciasViewModel(AppContainer.experienciasRepository) }
        }
    }
}

class DetalleExperienciaViewModel(id: String, repository: ExperienciasRepository) :
    CargaExperienciasViewModel<Experiencia?>({ if (id.isBlank()) null else repository.getExperiencia(id) }) {
    companion object {
        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetalleExperienciaViewModel(id, AppContainer.experienciasRepository) }
        }
    }
}
