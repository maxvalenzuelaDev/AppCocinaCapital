package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant

/** Implementación real: lee las tablas `categorias`, `locales` y `promociones` con supabase-kt (Postgrest). */
class SupabaseHomeRepository(
    private val supabase: SupabaseClient
) : HomeRepository {

    override suspend fun getCategorias(): List<Categoria> =
        supabase.from("categorias")
            .select { order("nombre", Order.ASCENDING) }
            .decodeList<Categoria>()

    override suspend fun getLocales(): List<LocalGastronomico> =
        supabase.from("locales")
            .select { filter { eq("activo", true) } }
            .decodeList<LocalGastronomico>()

    override suspend fun getPromocionesVigentes(): List<Promocion> {
        val ahora = Instant.now().toString()
        return supabase.from("promociones")
            .select {
                filter {
                    eq("activa", true)
                    lte("vigente_desde", ahora) // ya comenzó
                    gte("vigente_hasta", ahora) // aún no termina
                }
                order("vigente_hasta", Order.ASCENDING) // las que vencen antes, primero
            }
            .decodeList<Promocion>()
    }
}
