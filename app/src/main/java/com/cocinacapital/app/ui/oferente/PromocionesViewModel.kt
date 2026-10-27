package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.repository.PromocionesOferenteRepository
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * ViewModel de las promociones del oferente (HU-27): lista las de su local y crea nuevas con
 * vigencia, días, franja horaria y cupos. Una promoción activa y vigente aparece sola en el Inicio
 * del consumidor, que ya filtra por `activa` y por fechas.
 */
class PromocionesViewModel(
    private val promociones: PromocionesOferenteRepository,
    private val reloj: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow<PromocionesUiState>(PromocionesUiState.Loading)
    val uiState: StateFlow<PromocionesUiState> = _uiState.asStateFlow()

    private val _mensajes = Channel<Int>(Channel.BUFFERED)
    val mensajes: Flow<Int> = _mensajes.receiveAsFlow()

    /** Momento actual según el reloj del ViewModel, para calcular el estado de cada promoción. */
    val ahora: Instant get() = reloj.instant()

    /** Local cuyas promociones se muestran (el elegido en Mis locales). */
    private var localId: String? = null

    /** Muestra las promociones de [id]; `null` = el oferente aún no tiene locales. */
    fun mostrarLocal(id: String?) {
        val actual = _uiState.value
        if (id == localId && actual !is PromocionesUiState.Error && actual !is PromocionesUiState.Loading) return
        localId = id
        cargar()
    }

    fun onReintentar() = cargar()

    /** Abre el formulario con la vigencia sugerida: desde hoy y por una semana. */
    fun onNueva() = actualizar {
        val hoy = LocalDate.now(reloj)
        it.copy(formulario = FormularioPromocion(desde = hoy, hasta = hoy.plusDays(7)), errores = ErroresPromocion())
    }

    fun onCambio(cambio: FormularioPromocion.() -> FormularioPromocion) = actualizar { estado ->
        // Al corregir un campo se ocultan los errores; vuelven a validarse al guardar.
        estado.copy(formulario = estado.formulario?.cambio(), errores = ErroresPromocion())
    }

    fun onCerrarFormulario() = actualizar { if (it.guardando) it else it.copy(formulario = null, errores = ErroresPromocion()) }

    fun onGuardar() {
        val actual = _uiState.value as? PromocionesUiState.Success ?: return
        val formulario = actual.formulario ?: return
        if (actual.guardando) return
        val (datos, errores) = validarPromocion(formulario, LocalDate.now(reloj), reloj.zone)
        if (datos == null) {
            actualizar { it.copy(errores = errores) }
            avisar(R.string.promo_revisa_campos)
            return
        }
        actualizar { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                val creada = promociones.crear(actual.localId, datos)
                actualizar {
                    it.copy(promociones = listOf(creada) + it.promociones, formulario = null, guardando = false)
                }
                avisar(R.string.promo_creada)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // El formulario queda abierto con lo escrito para reintentar.
                actualizar { it.copy(guardando = false) }
                avisar(R.string.promo_error_guardar)
            }
        }
    }

    private fun cargar() {
        val id = localId
        if (id == null) { _uiState.value = PromocionesUiState.SinLocal; return }
        _uiState.value = PromocionesUiState.Loading
        viewModelScope.launch {
            val nuevo = try {
                PromocionesUiState.Success(id, promociones.listar(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PromocionesUiState.Error(R.string.promo_error_cargar)
            }
            // Si mientras cargaba se eligió otro local, este resultado ya no sirve.
            if (id == localId) _uiState.value = nuevo
        }
    }

    private fun actualizar(cambio: (PromocionesUiState.Success) -> PromocionesUiState.Success) =
        _uiState.update { if (it is PromocionesUiState.Success) cambio(it) else it }

    private fun avisar(@StringRes mensaje: Int) { _mensajes.trySend(mensaje) }

    companion object {
        val Factory = viewModelFactory {
            initializer { PromocionesViewModel(AppContainer.promocionesOferenteRepository) }
        }
    }
}
