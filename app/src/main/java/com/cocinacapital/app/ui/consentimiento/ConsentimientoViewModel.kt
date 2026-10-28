package com.cocinacapital.app.ui.consentimiento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EstadoConsentimiento {
    /** Leyendo las preferencias guardadas (Room responde casi al instante). */
    CARGANDO,
    /** Primer uso de esta cuenta en el teléfono: se muestra la pantalla. */
    PENDIENTE,
    RESPONDIDO
}

/**
 * Consentimiento de datos personales antes del primer uso (HU-03). La respuesta se guarda por
 * cuenta; aceptar o rechazar deja usar la app, y se puede cambiar después desde Perfil.
 */
class ConsentimientoViewModel(
    private val usuarioId: String,
    private val preferencias: PreferenciasRepository
) : ViewModel() {

    private val guardando = MutableStateFlow(false)

    /** Se trajeron (o se intentó traer) las preferencias de Supabase: en un teléfono nuevo ya respondió. */
    private val sincronizado = MutableStateFlow(false)

    val estado: StateFlow<EstadoConsentimiento> =
        combine(preferencias.consentimientoRespondido(usuarioId), guardando, sincronizado) { respondido, enviando, listo ->
            when {
                // Mientras se guarda la respuesta la app ya puede seguir: Room la confirma enseguida.
                respondido || enviando -> EstadoConsentimiento.RESPONDIDO
                !listo -> EstadoConsentimiento.CARGANDO
                else -> EstadoConsentimiento.PENDIENTE
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoConsentimiento.CARGANDO)

    init {
        // T-06: al iniciar sesión se sincronizan las preferencias. Sin red se sigue con lo del teléfono.
        viewModelScope.launch {
            try {
                withTimeout(ESPERA_SINCRONIZACION_MS) { preferencias.sincronizar(usuarioId) }
            } catch (_: TimeoutCancellationException) {
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            sincronizado.value = true
        }
    }

    fun onAceptar() = responder(true)

    fun onRechazar() = responder(false)

    private fun responder(acepta: Boolean) {
        if (guardando.value) return
        guardando.value = true
        viewModelScope.launch {
            try {
                preferencias.responderConsentimiento(usuarioId, acepta)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // No se pudo guardar: la pantalla vuelve a aparecer para responder de nuevo.
            } finally {
                guardando.value = false
            }
        }
    }

    companion object {
        private const val ESPERA_SINCRONIZACION_MS = 5_000L

        fun factory(usuarioId: String) = viewModelFactory {
            initializer { ConsentimientoViewModel(usuarioId, AppContainer.preferenciasRepository) }
        }
    }
}
