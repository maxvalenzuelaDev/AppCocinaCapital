package com.cocinacapital.app.di

import android.content.Context
import android.os.SystemClock
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.data.location.AndroidUbicacionRepository
import com.cocinacapital.app.data.location.identidadesUbicacion
import com.cocinacapital.location.AndroidLocationSource
import kotlinx.coroutines.*
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
import com.cocinacapital.app.data.repository.ExperienciasRepository
import com.cocinacapital.app.data.repository.FakeExperienciasRepository
import com.cocinacapital.app.data.repository.SupabaseExperienciasRepository
import com.cocinacapital.app.data.local.AndroidPreparadorFoto
import com.cocinacapital.app.data.local.PreparadorFoto
import com.cocinacapital.app.data.repository.CatalogoRepository
import com.cocinacapital.app.data.repository.FotosLocalRepository
import com.cocinacapital.app.data.repository.SupabaseFotosLocalRepository
import com.cocinacapital.app.data.repository.DetalleLocalRepository
import com.cocinacapital.app.data.repository.DetallePromocionRepository
import com.cocinacapital.app.data.repository.FakeDetallePromocionRepository
import com.cocinacapital.app.data.repository.SupabaseDetallePromocionRepository
import com.cocinacapital.app.data.repository.SupabaseCatalogoRepository
import com.cocinacapital.app.data.repository.PromocionesOferenteRepository
import com.cocinacapital.app.data.repository.SupabasePromocionesOferenteRepository
import com.cocinacapital.app.data.repository.FakeDetalleLocalRepository
import com.cocinacapital.app.data.repository.FakeHomeRepository
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.MisLocalesRepository
import com.cocinacapital.app.data.repository.SupabaseMisLocalesRepository
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.data.repository.SupabaseDetalleLocalRepository
import com.cocinacapital.app.data.repository.SupabaseHomeRepository
import com.cocinacapital.app.data.repository.RutaRepository
import com.cocinacapital.app.data.repository.OsrmRutaRepository
import com.cocinacapital.app.data.remote.crearClienteRutas
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/** Inyección de dependencias manual y simple (sin Hilt). Se inicializa en CocinaApp. */
object AppContainer {

    /** Cambiar a `false` cuando Supabase esté configurado y las tablas creadas. */
    private const val USE_FAKE_DATA = false

    private lateinit var appContext: Context

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun init(context: Context) {
        appContext = context.applicationContext
        if (SupabaseProvider.isConfigured) applicationScope.launch {
            authRepository.sessions.identidadesUbicacion().collect {
                ubicacionRepository.limpiar()
                rutaRepository.limpiar()
            }
        }
    }

    val ubicacionRepository: UbicacionRepository by lazy {
        AndroidUbicacionRepository(AndroidLocationSource(appContext), SystemClock::elapsedRealtime)
    }

    val rutaRepository: RutaRepository by lazy {
        // Cliente sin sesión ni cabeceras de Supabase: solo envía origen y destino al calcular una ruta.
        val rutas by lazy { crearClienteRutas() }
        OsrmRutaRepository(descargar = { url ->
            try {
                val response = rutas.get(url) {
                    header("User-Agent", "AppCocinaCapital/1.0 (+https://github.com/maxvalenzuelaDev/AppCocinaCapital)")
                }
                response.bodyAsText().also { body ->
                    if (com.cocinacapital.app.BuildConfig.DEBUG) android.util.Log.d("CocinaRutas",
                        "HTTP ${response.status.value}; bytes=${body.length}; json=${body.trimStart().startsWith('{')}")
                }
            } catch (error: Exception) {
                if (com.cocinacapital.app.BuildConfig.DEBUG) android.util.Log.w("CocinaRutas",
                    "HTTP ${error.javaClass.simpleName}; causa=${error.cause?.javaClass?.simpleName}")
                throw error
            }
        })
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

    val experienciasRepository: ExperienciasRepository by lazy {
        if (USE_FAKE_DATA) FakeExperienciasRepository()
        else SupabaseExperienciasRepository(SupabaseProvider.client)
    }

    val detalleLocalRepository: DetalleLocalRepository by lazy {
        if (USE_FAKE_DATA) FakeDetalleLocalRepository()
        else SupabaseDetalleLocalRepository(SupabaseProvider.client)
    }

    val preferenciasRepository: PreferenciasRepository by lazy {
        PreferenciasRepository(database.preferenciasDao(), perfilSesion)
    }

    val fotoPerfilArchivos: FotoPerfilArchivos by lazy { AndroidFotoPerfilArchivos(appContext) }

    val detallePromocionRepository: DetallePromocionRepository by lazy {
        if (USE_FAKE_DATA) FakeDetallePromocionRepository()
        else SupabaseDetallePromocionRepository(SupabaseProvider.client, detalleLocalRepository)
    }

    // ---- Oferente (HU-25 a HU-27): sus locales, protegidos por las políticas de la migración T-04 ----
    val misLocalesRepository: MisLocalesRepository by lazy { SupabaseMisLocalesRepository(SupabaseProvider.client) }
    val catalogoRepository: CatalogoRepository by lazy { SupabaseCatalogoRepository(SupabaseProvider.client) }

    // ---- Fotos de los locales (T-05): bucket `locales` de Supabase Storage ----
    val fotosLocalRepository: FotosLocalRepository by lazy { SupabaseFotosLocalRepository(SupabaseProvider.client) }
    val preparadorFoto: PreparadorFoto by lazy { AndroidPreparadorFoto(appContext) }
    val promocionesOferenteRepository: PromocionesOferenteRepository by lazy {
        SupabasePromocionesOferenteRepository(SupabaseProvider.client)
    }

    val favoritosRepository: FavoritosRepository by lazy {
        FavoritosRepository(database.favoritosDao(), perfilSesion)
    }
}
