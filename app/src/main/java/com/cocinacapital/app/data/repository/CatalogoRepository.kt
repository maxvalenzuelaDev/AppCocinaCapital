package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Plato
import kotlinx.serialization.Serializable

/** Datos de un plato que el oferente puede escribir (HU-26). [precio] en pesos, sin decimales. */
@Serializable
data class DatosPlato(
    val nombre: String,
    val descripcion: String = "",
    val precio: Int,
    /** Los destacados son los que ve el consumidor en "Menú destacado" del detalle del local. */
    val destacado: Boolean = false
)

/** Catálogo (platos) del local del oferente. Supabase (RLS) impide tocar platos de otros locales. */
interface CatalogoRepository {
    suspend fun listar(localId: String): List<Plato>
    suspend fun crear(localId: String, datos: DatosPlato): Plato
    suspend fun actualizar(platoId: String, datos: DatosPlato): Plato
    suspend fun eliminar(platoId: String)
}
