package com.cocinacapital.app.di

import android.content.Context
import com.cocinacapital.app.data.auth.PerfilSesion
import com.cocinacapital.app.data.auth.FakePerfilSesion
import com.cocinacapital.app.data.local.CocinaDatabase
import com.cocinacapital.app.data.remote.SupabaseProvider
import com.cocinacapital.app.data.repository.FakeHomeRepository
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.data.repository.SupabaseHomeRepository

/** Inyección de dependencias manual y simple (sin Hilt). Se inicializa en CocinaApp. */
object AppContainer {

    /** Cambia a `false` cuando Supabase esté configurado y las tablas creadas. */
    private const val USE_FAKE_DATA = true

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** TODO: reemplazar por la implementación real cuando se integre el Login de los compañeros. */
    val authGateway: PerfilSesion by lazy { FakePerfilSesion() }

    private val database: CocinaDatabase by lazy { CocinaDatabase.crear(appContext) }

    val homeRepository: HomeRepository by lazy {
        if (USE_FAKE_DATA) FakeHomeRepository()
        else SupabaseHomeRepository(SupabaseProvider.client)
    }

    val preferenciasRepository: PreferenciasRepository by lazy {
        PreferenciasRepository(database.preferenciasDao(), authGateway)
    }

    val favoritosRepository: FavoritosRepository by lazy {
        FavoritosRepository(database.favoritosDao(), authGateway)
    }
}
