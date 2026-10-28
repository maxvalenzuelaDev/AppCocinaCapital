package com.cocinacapital.app.ui.consentimiento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
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

    val estado: StateFlow<EstadoConsentimiento> =
        combine(preferencias.consentimientoRespondido(usuarioId), guardando) { respondido, enviando ->
            // Mientras se guarda la respuesta la app ya puede seguir: Room la confirma enseguida.
            if (respondido || enviando) EstadoConsentimiento.RESPONDIDO else EstadoConsentimiento.PENDIENTE
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoConsentimiento.CARGANDO)

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
        fun factory(usuarioId: String) = viewModelFactory {
            initializer { ConsentimientoViewModel(usuarioId, AppContainer.preferenciasRepository) }
        }
    }
}
