package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Implementación real: lee y escribe en `locales` el local cuyo `oferente_id` es la cuenta actual. */
class SupabaseMiLocalRepository(
    private val supabase: SupabaseClient
) : MiLocalRepository {

    private fun oferenteId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("No hay sesión iniciada")

    override suspend fun obtenerMiLocal(): LocalGastronomico? =
        supabase.from("locales")
            .select { filter { eq("oferente_id", oferenteId()) } }
            .decodeSingleOrNull<LocalGastronomico>()

    override suspend fun getCategorias(): List<Categoria> =
        supabase.from("categorias")
            .select { order("nombre", Order.ASCENDING) }
            .decodeList<Categoria>()

    override suspend fun guardar(localId: String?, datos: DatosLocal): LocalGastronomico =
        if (localId == null) {
            supabase.from("locales")
                .insert(NuevoLocal(oferenteId(), datos)) { select() }
                .decodeSingle<LocalGastronomico>()
        } else {
            supabase.from("locales")
                .update(datos) {
                    select()
                    filter { eq("id", localId); eq("oferente_id", oferenteId()) }
                }
                .decodeSingle<LocalGastronomico>()
        }
}

/** Fila nueva: los datos del formulario más el dueño del local. */
@Serializable
private data class NuevoLocal(
    @SerialName("oferente_id") val oferenteId: String,
    val nombre: String,
    @SerialName("categoria_id") val categoriaId: String,
    val comuna: String,
    val direccion: String,
    val descripcion: String,
    val horario: String?,
    @SerialName("ticket_promedio_persona") val ticketPromedioPersona: Long?
) {
    constructor(oferenteId: String, datos: DatosLocal) : this(
        oferenteId, datos.nombre, datos.categoriaId, datos.comuna, datos.direccion,
        datos.descripcion, datos.horario, datos.ticketPromedioPersona
    )
}
