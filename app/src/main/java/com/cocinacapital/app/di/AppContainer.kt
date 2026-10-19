package com.cocinacapital.app.di

import com.cocinacapital.app.data.remote.SupabaseProvider
import com.cocinacapital.app.data.repository.FakeHomeRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.SupabaseHomeRepository

/** Inyección de dependencias manual y simple (sin Hilt). */
object AppContainer {

    /** Cambia a `false` cuando Supabase esté configurado y las tablas creadas. */
    private const val USE_FAKE_DATA = true

    val homeRepository: HomeRepository by lazy {
        if (USE_FAKE_DATA) FakeHomeRepository()
        else SupabaseHomeRepository(SupabaseProvider.client)
    }
}
