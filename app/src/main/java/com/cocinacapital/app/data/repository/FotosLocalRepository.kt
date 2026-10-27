package com.cocinacapital.app.data.repository

/** Foto de portada de un local del oferente (T-05), guardada en Supabase Storage. */
interface FotosLocalRepository {
    /**
     * Sube [jpeg] como nueva portada de [localId], guarda su URL pública en `locales.imagen_url`
     * y devuelve esa URL. Después intenta borrar la foto [anterior] si era de este bucket.
     */
    suspend fun subirPortada(localId: String, jpeg: ByteArray, anterior: String?): String
}
