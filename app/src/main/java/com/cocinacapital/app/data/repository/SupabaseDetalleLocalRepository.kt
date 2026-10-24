package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant

/** Implementación real: lee `locales`, `categorias`, `platos` y `promociones` filtrando por el local. */
class SupabaseDetalleLocalRepository(
    private val supabase: SupabaseClient
) : DetalleLocalRepository {

    override suspend fun getLocal(id: String): LocalGastronomico? {
        val local = supabase.from("locales")
            .select { filter { eq("id", id); eq("activo", true) } }
            .decodeSingleOrNull<LocalGastronomico>() ?: return null
        val categoria = supabase.from("categorias")
            .select { filter { eq("id", local.categoriaId) } }
            .decodeSingleOrNull<Categoria>()
        return local.copy(categoriaNombre = categoria?.nombre.orEmpty())
    }

    override suspend fun getPlatosDestacados(localId: String): List<Plato> =
        supabase.from("platos")
            .select {
                filter { eq("local_id", localId); eq("destacado", true) }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<Plato>()

    override suspend fun getPromocionesVigentes(localId: String): List<Promocion> {
        val ahora = Instant.now().toString()
        return supabase.from("promociones")
            .select {
                filter {
                    eq("local_id", localId)
                    eq("activa", true)
                    lte("vigente_desde", ahora)
                    gte("vigente_hasta", ahora)
                }
                order("vigente_hasta", Order.ASCENDING)
            }
            .decodeList<Promocion>()
    }
}
