package com.cocinacapital.app.data.remote

import com.cocinacapital.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Único cliente de Supabase de toda la app (singleton).
 *
 * IMPORTANTE para el equipo: Login/Registro deben usar ESTE mismo cliente. Cuando ellos
 * necesiten autenticación, solo hay que agregar `install(Auth)` dentro del builder.
 */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            // install(Auth)  // ← lo agregan los compañeros de Login/Registro
        }
    }
}
