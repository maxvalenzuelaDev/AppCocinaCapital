package com.cocinacapital.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Implementación real sobre el bucket público `locales` (migración T-05). Cada foto va en la carpeta
 * de su local con un nombre nuevo, así Coil no muestra la anterior desde su caché.
 */
class SupabaseFotosLocalRepository(
    private val supabase: SupabaseClient,
    private val ahora: () -> Long = System::currentTimeMillis
) : FotosLocalRepository {

    private val bucket get() = supabase.storage.from(BUCKET)

    override suspend fun subirPortada(localId: String, jpeg: ByteArray, anterior: String?): String {
        val ruta = "$localId/portada-${ahora()}.jpg"
        bucket.upload(ruta, jpeg) { contentType = ContentType.Image.JPEG }
        val url = bucket.publicUrl(ruta)
        supabase.from("locales").update(buildJsonObject { put("imagen_url", url) }) {
            filter { eq("id", localId) }
        }
        anterior?.let { borrarSiEsNuestra(it) }
        return url
    }

    /** La foto anterior sobra; si no se puede borrar, no afecta al oferente. */
    private suspend fun borrarSiEsNuestra(url: String) {
        val ruta = url.substringAfter("/object/public/$BUCKET/", missingDelimiterValue = "")
        if (ruta.isEmpty()) return
        try {
            bucket.delete(ruta)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val BUCKET = "locales"
    }
}
