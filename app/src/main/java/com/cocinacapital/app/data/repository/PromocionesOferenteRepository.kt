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

    // ---- HU-28: cada una devuelve la promoción actualizada ----
    /** Cambia los datos de la promoción; los cupos no se tocan (los maneja el canje). */
    suspend fun actualizar(promocionId: String, datos: DatosPromocion): Promocion
    /** Pausar (`false`) o reanudar (`true`): una promoción inactiva no la ve el consumidor. */
    suspend fun cambiarActiva(promocionId: String, activa: Boolean): Promocion
    /** Cambia la vigencia; se usa para finalizarla ahora. [vigenteDesde] `null` = no cambia. */
    suspend fun cambiarVigencia(promocionId: String, vigenteDesde: String?, vigenteHasta: String): Promocion

    /** HU-30: contadores por promoción del local, por id de promoción. */
    suspend fun metricas(localId: String): Map<String, MetricasPromocion> = emptyMap()
}

/** HU-30: vistas del detalle (de quienes dieron consentimiento), beneficios obtenidos y canjeados. */
@Serializable
data class MetricasPromocion(
    @SerialName("promocion_id") val promocionId: String,
    val vistas: Int,
    val obtenidos: Int,
    val canjeados: Int
)
