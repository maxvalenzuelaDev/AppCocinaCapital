package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.R
import com.cocinacapital.app.data.local.LectorQr
import com.cocinacapital.app.data.model.CodigoBeneficio
import com.cocinacapital.app.data.model.ResultadoCanje
import com.cocinacapital.app.data.repository.BilleteraException
import com.cocinacapital.app.data.repository.CanjeRepository
import com.cocinacapital.app.data.repository.MotivoBilletera
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pantalla Canjear del oferente (HU-15 y HU-29). */
data class CanjeUiState(
    /** Lo que escribe el oferente, ya en mayúsculas. */
    val codigo: String = "",
    @StringRes val error: Int? = null,
    /** Revisando el código, leyendo el QR o canjeando. */
    val ocupado: Boolean = false,
    /** Resultado de la revisión; tras confirmar, el del canje. */
    val resultado: ResultadoCanje? = null
)

/**
 * Primero se revisa el código (válido, vencido, ya canjeado…) y el oferente confirma el canje:
 * así ve qué promoción aplica antes de marcarla usada. Supabase solo encuentra los códigos de
 * promociones de sus locales y bloquea el beneficio para que no se canjee dos veces.
 */
class CanjeViewModel(
    private val canje: CanjeRepository,
    private val lector: LectorQr
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanjeUiState())
    val uiState: StateFlow<CanjeUiState> = _uiState.asStateFlow()

    /** Foto pedida a la cámara, para leerla o borrarla al volver. */
    private var captura: String? = null

    fun onCodigo(texto: String) {
        val limpio = texto.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(CodigoBeneficio.LARGO + 1)
        _uiState.update { it.copy(codigo = limpio, error = null, resultado = null) }
    }

    fun onRevisar() {
        val codigo = CodigoBeneficio.desdeTexto(_uiState.value.codigo)
        if (codigo == null) {
            _uiState.update { it.copy(error = R.string.canje_codigo_invalido, resultado = null) }
            return
        }
        revisar(codigo)
    }

    fun onConfirmar() {
        val actual = _uiState.value
        val resultado = actual.resultado ?: return
        if (actual.ocupado) return
        llamar { canje.canjear(resultado.codigo) }
    }

    /** Deja la pantalla lista para el siguiente cliente. */
    fun onNuevo() = _uiState.update { CanjeUiState() }

    /** Uri donde la cámara debe guardar la foto del QR. */
    fun onEscanear(): String {
        captura?.let(lector::descartar)
        return lector.nuevaCaptura().also { captura = it.ruta }.uri
    }

    fun onFotoTomada(exito: Boolean) {
        val ruta = captura ?: return
        captura = null
        if (!exito) {
            lector.descartar(ruta)
            return
        }
        _uiState.update { it.copy(ocupado = true, error = null, resultado = null) }
        viewModelScope.launch {
            val texto = try {
                lector.leer(ruta)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val codigo = texto?.let(CodigoBeneficio::desdeTexto)
            when {
                texto == null -> _uiState.update { it.copy(ocupado = false, error = R.string.canje_qr_no_leido) }
                codigo == null -> _uiState.update { it.copy(ocupado = false, error = R.string.canje_qr_ajeno) }
                else -> revisar(codigo)
            }
        }
    }

    fun onPermisoCamaraDenegado() = _uiState.update { it.copy(error = R.string.canje_permiso_camara) }

    private fun revisar(codigo: String) {
        _uiState.update { it.copy(codigo = CodigoBeneficio.formatear(codigo)) }
        llamar { canje.consultar(codigo) }
    }

    private fun llamar(accion: suspend () -> ResultadoCanje) {
        _uiState.update { it.copy(ocupado = true, error = null) }
        viewModelScope.launch {
            try {
                val resultado = accion()
                _uiState.update { it.copy(ocupado = false, resultado = resultado) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: BilleteraException) {
                _uiState.update { it.copy(ocupado = false, resultado = null, error = mensajeDe(error.motivo)) }
            } catch (_: Exception) {
                // Se conserva el resultado anterior: si falló al confirmar, se puede volver a intentar.
                _uiState.update { it.copy(ocupado = false, error = R.string.canje_error_red) }
            }
        }
    }

    override fun onCleared() {
        captura?.let(lector::descartar)
    }

    companion object {
        @StringRes
        fun mensajeDe(motivo: MotivoBilletera): Int = when (motivo) {
            MotivoBilletera.NO_AUTORIZADO, MotivoBilletera.SIN_SESION -> R.string.canje_error_no_autorizado
            MotivoBilletera.CODIGO_NO_ENCONTRADO -> R.string.canje_error_no_encontrado
            else -> R.string.canje_error_red
        }

        val Factory = viewModelFactory {
            initializer { CanjeViewModel(AppContainer.canjeRepository, AppContainer.lectorQr) }
        }
    }
}
