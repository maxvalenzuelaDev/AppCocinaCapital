package com.cocinacapital.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/** Estado de un beneficio para el consumidor (HU-14). "Vencido" lo calcula Supabase con la promoción. */
@Serializable
enum class EstadoBeneficio {
    @SerialName("vigente") VIGENTE,
    @SerialName("canjeado") CANJEADO,
    @SerialName("vencido") VENCIDO
}

/**
 * Beneficio de la billetera con los datos de su promoción y su local: una fila de la función
 * `mis_beneficios()` (migración T-03), que los trae en una sola consulta.
 */
@Serializable
data class BeneficioUsuario(
    val id: String,
    val codigo: String,
    val estado: EstadoBeneficio,
    @SerialName("guardado_en") val guardadoEn: String,
    @SerialName("canjeado_en") val canjeadoEn: String? = null,
    @SerialName("promocion_id") val promocionId: String,
    val titulo: String,
    val descripcion: String = "",
    val condiciones: String = "",
    @SerialName("descuento_porcentaje") val descuentoPorcentaje: Int,
    @SerialName("vigente_hasta") val vigenteHasta: String,
    @SerialName("local_id") val localId: String,
    @SerialName("local_nombre") val localNombre: String
) {
    /** Con la billetera abierta, un beneficio vigente pasa a vencido al terminar la promoción. */
    fun estadoEn(ahora: Instant): EstadoBeneficio {
        if (estado != EstadoBeneficio.VIGENTE) return estado
        val fin = runCatching { OffsetDateTime.parse(vigenteHasta).toInstant() }.getOrNull() ?: return estado
        return if (ahora >= fin) EstadoBeneficio.VENCIDO else estado
    }
}

/** Fila propia de `beneficios_usuario`: lo que devuelve `adquirir_beneficio` y lo que mira el detalle. */
@Serializable
data class BeneficioObtenido(
    val id: String,
    val codigo: String,
    val estado: EstadoBeneficio = EstadoBeneficio.VIGENTE,
    @SerialName("promocion_id") val promocionId: String
)
