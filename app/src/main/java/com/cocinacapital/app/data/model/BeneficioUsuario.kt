package com.cocinacapital.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representa una promoción guardada en la billetera.
 *
 * Se relaciona con la tabla beneficios_usuario
 * de Supabase.
 */
@Serializable
data class BeneficioUsuario(

    // Identificador del beneficio guardado.
    val id: String,

    // Identificador del consumidor.
    @SerialName("usuario_id")
    val usuarioId: String,

    // Identificador de la promoción guardada.
    @SerialName("promocion_id")
    val promocionId: String,

    // Fecha en que se guardó la promoción.
    @SerialName("guardado_en")
    val guardadoEn: String
)
