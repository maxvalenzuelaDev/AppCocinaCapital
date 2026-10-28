package com.cocinacapital.app.data.remote

import com.cocinacapital.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage

/**
 * Único cliente de Supabase de toda la app (singleton).
 *
 * Login y Home usan ESTE cliente. El registro del compañero también debe usarlo
 * cuando se incorpore, para compartir sesión y renovación de tokens.
 */
object SupabaseProvider {
    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth)
            install(Storage) // fotos de los locales (T-05)
        }
    }
}
