package com.cocinacapital.app.ui.billetera

import androidx.annotation.StringRes
import com.cocinacapital.app.data.model.BeneficioObtenido
import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.EstadoBeneficio
import java.time.Instant

/** Estado de la pantalla Billetera (HU-14 y HU-16). */
sealed interface BilleteraUiState {
    data object Loading : BilleteraUiState
    data class Error(@StringRes val mensajeRes: Int) : BilleteraUiState
    data class Success(
        val beneficios: List<BeneficioUsuario>,
        /** Beneficio cuyo código y QR se muestran en pantalla completa. */
        val mostrando: BeneficioUsuario? = null,
        /** Beneficio que espera confirmación para quitarse. */
        val porQuitar: BeneficioUsuario? = null,
        val quitandoId: String? = null
    ) : BilleteraUiState {
        /** Los que aún se pueden usar, en el orden en que se obtuvieron. */
        fun vigentes(ahora: Instant) = beneficios.filter { it.estadoEn(ahora) == EstadoBeneficio.VIGENTE }
        /** Canjeados y vencidos (HU-16). */
        fun historial(ahora: Instant) = beneficios.filter { it.estadoEn(ahora) != EstadoBeneficio.VIGENTE }
    }
}

/** Botón del detalle de una promoción para obtener su beneficio (HU-13). */
data class BeneficioPromocionUiState(
    /** Revisando si la persona ya tiene el beneficio de esta promoción. */
    val cargando: Boolean = true,
    val beneficio: BeneficioObtenido? = null,
    val obteniendo: Boolean = false,
    @StringRes val error: Int? = null,
    /** Diálogo con el código recién obtenido o pedido con "Ver mi código". */
    val mostrarCodigo: Boolean = false,
    /** El diálogo se abrió al obtenerlo: confirma que quedó guardado en la billetera. */
    val recienObtenido: Boolean = false
)
