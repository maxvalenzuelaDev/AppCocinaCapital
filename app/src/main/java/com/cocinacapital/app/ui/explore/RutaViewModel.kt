package com.cocinacapital.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.data.repository.RutaNoEncontrada
import com.cocinacapital.app.data.repository.RutaRepository
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.ui.home.UbicacionUsuario
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contexto conservado al cargar, reintentar o cambiar el modo de viaje. */
data class ContextoRuta(val localId: String? = null, val solicitud: SolicitudRuta? = null,
    val modo: ModoRuta = ModoRuta.A_PIE)

sealed interface RutaUiState {
    val contexto: ContextoRuta
    val localId get() = contexto.localId
    val solicitud get() = contexto.solicitud
    val modo get() = contexto.modo
    val ruta: Ruta? get() = null

    data class SinLocal(override val contexto: ContextoRuta = ContextoRuta()) : RutaUiState
    data class SinUbicacion(override val contexto: ContextoRuta) : RutaUiState
    data class Pendiente(override val contexto: ContextoRuta) : RutaUiState
    data class Loading(override val contexto: ContextoRuta) : RutaUiState
    data class Success(override val contexto: ContextoRuta, override val ruta: Ruta) : RutaUiState
    data class Error(override val contexto: ContextoRuta, val sinRuta: Boolean = false) : RutaUiState
}

class RutaViewModel(private val repository: RutaRepository) : ViewModel() {
    private val mutable = MutableStateFlow<RutaUiState>(RutaUiState.SinLocal())
    val estado = mutable.asStateFlow()
    private var consulta: Job? = null
    private var version = 0L

    fun configurar(local: LocalGastronomico?, origen: UbicacionUsuario?, zonaManual: Boolean) {
        val modo = mutable.value.modo
        val request = if (local?.latitud != null && local.longitud != null && origen != null && !zonaManual)
            SolicitudRuta(local.id, PuntoRuta(origen.latitud, origen.longitud), PuntoRuta(local.latitud, local.longitud), modo)
            else null
        if (mutable.value.localId == local?.id && mutable.value.solicitud == request) return
        cancelar()
        val contexto = ContextoRuta(local?.id, request, modo)
        mutable.value = when {
            local == null -> RutaUiState.SinLocal(contexto)
            request == null -> RutaUiState.SinUbicacion(contexto)
            else -> RutaUiState.Pendiente(contexto)
        }
    }

    fun modo(modo: ModoRuta) {
        val anterior = mutable.value
        if (anterior.modo == modo) return
        val recalcular = anterior is RutaUiState.Success || anterior is RutaUiState.Loading || anterior is RutaUiState.Error
        cancelar()
        val contexto = anterior.contexto.copy(modo = modo, solicitud = anterior.solicitud?.copy(modo = modo))
        mutable.value = when {
            contexto.localId == null -> RutaUiState.SinLocal(contexto)
            contexto.solicitud == null -> RutaUiState.SinUbicacion(contexto)
            else -> RutaUiState.Pendiente(contexto)
        }
        if (recalcular) calcular()
    }

    fun calcular() {
        val request = mutable.value.solicitud ?: return
        cancelar()
        val generation = version
        val contexto = mutable.value.contexto
        mutable.value = RutaUiState.Loading(contexto)
        consulta = viewModelScope.launch {
            try {
                val result = repository.calcular(request)
                if (generation == version) mutable.value = RutaUiState.Success(contexto, result)
            } catch (error: CancellationException) { throw error }
            catch (_: RutaNoEncontrada) { if (generation == version) mutable.value = RutaUiState.Error(contexto, sinRuta = true) }
            catch (_: Exception) { if (generation == version) mutable.value = RutaUiState.Error(contexto) }
        }
    }

    fun cancelar() {
        version++; consulta?.cancel(); consulta = null
        val anterior = mutable.value
        if (anterior is RutaUiState.Loading) mutable.value = RutaUiState.Pendiente(anterior.contexto)
    }

    companion object { val Factory = viewModelFactory { initializer { RutaViewModel(AppContainer.rutaRepository) } } }
}
