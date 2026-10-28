package com.cocinacapital.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fila de `preferencias_usuario` en Supabase (migración T-06). La foto de perfil queda solo en el teléfono.
 * Sin valores por defecto a propósito: kotlinx.serialization omite al enviar los campos iguales a su
 * valor por defecto, y entonces volver a "sin categorías" o a "MEDIO" no llegaría a Supabase.
 */
@Serializable
data class PreferenciasRemotasDto(
    @SerialName("usuario_id") val usuarioId: String,
    val categorias: List<String>,
    @SerialName("rango_precio") val rangoPrecio: String,
    val notificaciones: Boolean,
    @SerialName("solo_cercanas") val soloCercanas: Boolean,
    @SerialName("solo_categorias_favoritas") val soloCategoriasFavoritas: Boolean,
    val tema: String,
    val consentimiento: Boolean,
    /** ISO 8601, como lo guarda Supabase; `null` también se envía para borrarla al revocar. */
    @SerialName("consentimiento_fecha") val consentimientoFecha: String?,
    @SerialName("consentimiento_respondido") val consentimientoRespondido: Boolean
)

/** Copia en Supabase de las preferencias y registro de consentimientos (HU-02, HU-03, T-06). */
interface PreferenciasRemotas {
    suspend fun obtener(usuarioId: String): PreferenciasRemotasDto?
    suspend fun guardar(preferencias: PreferenciasRemotasDto)
    /** Agrega una fila al historial; la fecha la pone Supabase. */
    suspend fun registrarConsentimiento(acepto: Boolean)
}

/** RLS limita cada consulta a las filas de quien tiene sesión. */
class SupabasePreferenciasRemotas(private val supabase: SupabaseClient) : PreferenciasRemotas {

    override suspend fun obtener(usuarioId: String): PreferenciasRemotasDto? =
        supabase.from("preferencias_usuario")
            .select { filter { eq("usuario_id", usuarioId) } }
            .decodeSingleOrNull<PreferenciasRemotasDto>()

    override suspend fun guardar(preferencias: PreferenciasRemotasDto) {
        supabase.from("preferencias_usuario").upsert(preferencias) { onConflict = "usuario_id" }
    }

    override suspend fun registrarConsentimiento(acepto: Boolean) {
        supabase.from("consentimientos").insert(buildJsonObject { put("acepto", acepto) })
    }
}
