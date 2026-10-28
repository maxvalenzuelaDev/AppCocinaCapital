package com.cocinacapital.app.ui.billetera

import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.PromocionGuardada

/**
 * Estado de la pantalla Billetera.
 *
 * Controla la carga, los beneficios guardados,
 * las promociones completas y los errores.
 */
data class BilleteraUiState(

    // Indica si se están consultando los beneficios.
    val cargando: Boolean = false,

    // Registros de beneficios_usuario.
    // También sirven para identificar promociones ya guardadas.
    val beneficios: List<BeneficioUsuario> = emptyList(),

    // Promociones con título, descuento, vigencia y nombre del local.
    val promocionesGuardadas: List<PromocionGuardada> = emptyList(),

    // Mensaje si ocurre un error.
    val mensajeError: String? = null,

    // Indica si se está guardando o eliminando un beneficio.
    val procesando: Boolean = false
)
