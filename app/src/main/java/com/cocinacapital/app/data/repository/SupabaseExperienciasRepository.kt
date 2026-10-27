package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Experiencia
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID

/** El join evita consultas por cada tarjeta y oculta experiencias de locales inactivos. */
class SupabaseExperienciasRepository(private val supabase: SupabaseClient) : ExperienciasRepository {
    override suspend fun listar(): List<Experiencia> = supabase.from("experiencias")
        .select(Columns.raw("*,locales!inner(*)")) {
            filter { eq("activa", true); eq("locales.activo", true) }
            order("titulo", Order.ASCENDING)
            order("id", Order.ASCENDING)
        }.decodeList<Experiencia>().filter { it.activa && it.local?.id == it.localId }

    override suspend fun getExperiencia(id: String): Experiencia? {
        if (!runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)) return null
        return supabase.from("experiencias")
            .select(Columns.raw("*,locales!inner(*)")) {
                filter { eq("id", id); eq("activa", true); eq("locales.activo", true) }
            }.decodeSingleOrNull<Experiencia>()?.takeIf { it.activa && it.local?.id == it.localId }
    }
}
