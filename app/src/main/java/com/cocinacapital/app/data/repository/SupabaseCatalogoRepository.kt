package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Plato
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Implementación real: escribe en `platos`; las políticas de la migración T-04 validan el dueño. */
class SupabaseCatalogoRepository(
    private val supabase: SupabaseClient
) : CatalogoRepository {

    override suspend fun listar(localId: String): List<Plato> =
        supabase.from("platos")
            .select {
                filter { eq("local_id", localId) }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<Plato>()

    override suspend fun crear(localId: String, datos: DatosPlato): Plato =
        supabase.from("platos")
            .insert(NuevoPlato(localId, datos.nombre, datos.descripcion, datos.precio, datos.destacado)) { select() }
            .decodeSingle<Plato>()

    override suspend fun actualizar(platoId: String, datos: DatosPlato): Plato =
        supabase.from("platos")
            .update(datos) {
                select()
                filter { eq("id", platoId) }
            }
            .decodeSingle<Plato>()

    override suspend fun eliminar(platoId: String) {
        // Si RLS no lo permite, Supabase no borra nada y tampoco da error: se comprueba con la fila devuelta.
        val borrados = supabase.from("platos")
            .delete {
                select()
                filter { eq("id", platoId) }
            }
            .decodeList<Plato>()
        check(borrados.isNotEmpty()) { "El plato no se pudo eliminar" }
    }
}

/** Fila nueva: los datos del formulario más el local al que pertenece. */
@Serializable
private data class NuevoPlato(
    @SerialName("local_id") val localId: String,
    val nombre: String,
    val descripcion: String,
    val precio: Int,
    val destacado: Boolean
)
