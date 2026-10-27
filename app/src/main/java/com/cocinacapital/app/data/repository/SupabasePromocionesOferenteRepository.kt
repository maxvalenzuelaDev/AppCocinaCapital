package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Promocion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Implementación real sobre `promociones`; las políticas de T-04 y HU-27 validan el dueño y las columnas. */
class SupabasePromocionesOferenteRepository(
    private val supabase: SupabaseClient
) : PromocionesOferenteRepository {

    override suspend fun listar(localId: String): List<Promocion> =
        supabase.from("promociones")
            .select {
                filter { eq("local_id", localId) }
                order("vigente_hasta", Order.DESCENDING) // las más recientes primero
            }
            .decodeList<Promocion>()

    override suspend fun crear(localId: String, datos: DatosPromocion): Promocion =
        supabase.from("promociones")
            .insert(NuevaPromocion(localId, datos)) { select() }
            .decodeSingle<Promocion>()
}

/** Fila nueva: los datos del formulario más el local; nace activa. */
@Serializable
private data class NuevaPromocion(
    @SerialName("local_id") val localId: String,
    val titulo: String,
    val descripcion: String,
    val condiciones: String,
    @SerialName("descuento_porcentaje") val descuentoPorcentaje: Int,
    @SerialName("vigente_desde") val vigenteDesde: String,
    @SerialName("vigente_hasta") val vigenteHasta: String,
    @SerialName("dias_semana") val diasSemana: List<Int>?,
    @SerialName("hora_inicio") val horaInicio: String?,
    @SerialName("hora_fin") val horaFin: String?,
    @SerialName("cupos_total") val cuposTotal: Int?,
    val activa: Boolean = true
) {
    constructor(localId: String, d: DatosPromocion) : this(
        localId, d.titulo, d.descripcion, d.condiciones, d.descuentoPorcentaje, d.vigenteDesde, d.vigenteHasta,
        d.diasSemana, d.horaInicio, d.horaFin, d.cuposTotal
    )
}
