package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Promocion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Datos de una promoción que escribe el oferente (HU-27). Fechas en ISO 8601 con zona horaria;
 * horas como "HH:mm". `cupos_disponibles` no se envía: lo inicia Supabase igual al total.
 */
@Serializable
data class DatosPromocion(
    val titulo: String,
    val descripcion: String = "",
    val condiciones: String = "",
    @SerialName("descuento_porcentaje") val descuentoPorcentaje: Int,
    @SerialName("vigente_desde") val vigenteDesde: String,
    @SerialName("vigente_hasta") val vigenteHasta: String,
    @SerialName("dias_semana") val diasSemana: List<Int>? = null,
    @SerialName("hora_inicio") val horaInicio: String? = null,
    @SerialName("hora_fin") val horaFin: String? = null,
    @SerialName("cupos_total") val cuposTotal: Int? = null
)

/** Promociones del local del oferente, incluidas las vencidas e inactivas (RLS limita a las suyas). */
interface PromocionesOferenteRepository {
    suspend fun listar(localId: String): List<Promocion>
    suspend fun crear(localId: String, datos: DatosPromocion): Promocion
}
