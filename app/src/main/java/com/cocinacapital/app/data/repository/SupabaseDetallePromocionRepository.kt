package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class SupabaseDetallePromocionRepository(
    private val supabase: SupabaseClient,
    private val locales: DetalleLocalRepository
) : DetallePromocionRepository {
    override suspend fun getPromocion(id: String): PromocionDetalle? {
        val idValido = runCatching {
            UUID.fromString(id).toString().equals(id, ignoreCase = true)
        }.getOrDefault(false)
        if (!idValido) return null

        val respuesta = supabase.from("promociones")
            .select { filter { eq("id", id) } }
        val promocion = respuesta.decodeSingleOrNull<Promocion>() ?: return null
        val campos = Json.parseToJsonElement(respuesta.data).jsonArray.single().jsonObject.keys
        return PromocionDetalle(
            promocion = promocion,
            cuposConfigurados = setOf("cupos_total", "cupos_disponibles").all { it in campos },
            horarioConfigurado = setOf("dias_semana", "hora_inicio", "hora_fin").all { it in campos }
        )
    }

    override suspend fun getLocal(localId: String): LocalGastronomico? =
        locales.getLocal(localId)
}
