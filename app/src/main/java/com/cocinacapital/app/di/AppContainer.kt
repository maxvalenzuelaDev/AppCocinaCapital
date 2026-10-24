package com.cocinacapital.app.di

import android.content.Context
import com.cocinacapital.app.data.auth.LoginRepository
import com.cocinacapital.app.data.auth.PerfilSesion
import com.cocinacapital.app.data.auth.SupabaseAuthGateway
import com.cocinacapital.app.data.auth.SupabasePerfilSesion
import com.cocinacapital.app.data.local.AndroidFotoPerfilArchivos
import com.cocinacapital.app.data.local.CocinaDatabase
import com.cocinacapital.app.data.local.FotoPerfilArchivos
import com.cocinacapital.app.data.local.HistorialSesion
import com.cocinacapital.app.data.local.PreferenciasHistorialSesion
import com.cocinacapital.app.data.remote.SupabaseProvider
import com.cocinacapital.app.data.repository.DetalleLocalRepository
import com.cocinacapital.app.data.repository.FakeDetalleLocalRepository
import com.cocinacapital.app.data.repository.FakeHomeRepository
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.data.repository.SupabaseDetalleLocalRepository
import com.cocinacapital.app.data.repository.SupabaseHomeRepository

/** Inyección de dependencias manual y simple (sin Hilt). Se inicializa en CocinaApp. */
object AppContainer {

    /** Cambiar a `false` cuando Supabase esté configurado y las tablas creadas. */
    private const val USE_FAKE_DATA = false

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---- Login (Nevan): usa el cliente compartido de Supabase ----
    val authRepository: LoginRepository by lazy {
        LoginRepository(SupabaseAuthGateway(SupabaseProvider.client))
    }

    // ---- Perfil: el usuario sale de la misma sesión de Supabase ----
    val perfilSesion: PerfilSesion by lazy { SupabasePerfilSesion(SupabaseProvider.client) }

    /** Si alguna vez hubo sesión en este teléfono (saludo del Login). */
    val historialSesion: HistorialSesion by lazy { PreferenciasHistorialSesion(appContext) }

    private val database: CocinaDatabase by lazy { CocinaDatabase.crear(appContext) }

    val homeRepository: HomeRepository by lazy {
        if (USE_FAKE_DATA) FakeHomeRepository()
        else SupabaseHomeRepository(SupabaseProvider.client)
    }

    val detalleLocalRepository: DetalleLocalRepository by lazy {
        if (USE_FAKE_DATA) FakeDetalleLocalRepository()
        else SupabaseDetalleLocalRepository(SupabaseProvider.client)
    }

    val preferenciasRepository: PreferenciasRepository by lazy {
        PreferenciasRepository(database.preferenciasDao(), perfilSesion)
    }

    val fotoPerfilArchivos: FotoPerfilArchivos by lazy { AndroidFotoPerfilArchivos(appContext) }

    val favoritosRepository: FavoritosRepository by lazy {
        FavoritosRepository(database.favoritosDao(), perfilSesion)
    }
}