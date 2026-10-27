package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Datos del perfil del local que el oferente puede escribir (HU-25). Coinciden con las columnas
 * que la migración T-04 le permite insertar o actualizar en `locales`.
 */
@Serializable
data class DatosLocal(
    val nombre: String,
    @SerialName("categoria_id") val categoriaId: String,
    val comuna: String,
    val direccion: String,
    val descripcion: String = "",
    val horario: String? = null,
    @SerialName("ticket_promedio_persona") val ticketPromedioPersona: Long? = null,
    /** Posición del local; sin ella no aparece en Explorar ni en el mapa del consumidor. */
    val latitud: Double? = null,
    val longitud: Double? = null
)

/** Local del oferente con sesión iniciada. Supabase (RLS) impide leer o editar locales ajenos. */
interface MiLocalRepository {
    /** El local de la cuenta actual, o `null` si todavía no registra uno. */
    suspend fun obtenerMiLocal(): LocalGastronomico?
    suspend fun getCategorias(): List<Categoria>
    /** Crea el local si [localId] es `null`; si no, actualiza ese local. Devuelve el local guardado. */
    suspend fun guardar(localId: String?, datos: DatosLocal): LocalGastronomico
}
