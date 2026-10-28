package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.PerfilSesion
import com.cocinacapital.app.data.local.FavoritosDao
import com.cocinacapital.app.data.local.FotoPerfilArchivos
import com.cocinacapital.app.data.local.PreferenciasDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lo que la app registra (HU-20). Los canjes los registra Supabase al canjear. */
enum class TipoInteraccion(val valor: String) {
    BUSQUEDA("busqueda"),
    VISTA_LOCAL("vista_local"),
    VISTA_PROMOCION("vista_promocion")
}

data class Interaccion(
    val tipo: TipoInteraccion,
    val localId: String? = null,
    val promocionId: String? = null,
    val termino: String? = null
)

/**
 * Registro de interacciones para recomendar ofertas (HU-20, RF-13). Solo se envía con el
 * consentimiento activo (HU-03), y Supabase vuelve a revisarlo. No bloquea a quien lo llama: un
 * fallo de red solo hace que esa interacción no quede registrada.
 */
fun interface RegistroInteracciones {
    fun registrar(interaccion: Interaccion)

    companion object {
        /** Para pruebas y pantallas sin registro. */
        val Ninguno = RegistroInteracciones {}
    }
}

class SupabaseRegistroInteracciones(
    private val supabase: SupabaseClient,
    private val hayConsentimiento: suspend () -> Boolean,
    private val segundoPlano: CoroutineScope
) : RegistroInteracciones {

    override fun registrar(interaccion: Interaccion) {
        segundoPlano.launch {
            runCatching {
                if (!hayConsentimiento()) return@launch
                supabase.from("interacciones").insert(buildJsonObject {
                    put("tipo", interaccion.tipo.valor)
                    interaccion.localId?.let { put("local_id", it) }
                    interaccion.promocionId?.let { put("promocion_id", it) }
                    interaccion.termino?.let { put("termino", it.take(LARGO_MAXIMO_TERMINO)) }
                })
            }
        }
    }

    private companion object {
        const val LARGO_MAXIMO_TERMINO = 100
    }
}

/** Derechos sobre los datos desde Perfil (T-07, Ley 21.719). */
interface GestionCuenta {
    /** Borra las interacciones guardadas en Supabase; devuelve cuántas eran. */
    suspend fun borrarHistorial(): Int

    /**
     * Elimina la cuenta en Supabase (y en cascada todos sus datos), borra lo guardado en este
     * teléfono y cierra la sesión: la app vuelve al Login.
     */
    suspend fun eliminarCuenta()
}

class SupabaseGestionCuenta(
    private val supabase: SupabaseClient,
    private val sesion: PerfilSesion,
    private val preferencias: PreferenciasDao,
    private val favoritos: FavoritosDao,
    private val fotos: FotoPerfilArchivos
) : GestionCuenta {

    override suspend fun borrarHistorial(): Int =
        supabase.postgrest.rpc("borrar_historial").decodeAs<Int>()

    override suspend fun eliminarCuenta() {
        val id = sesion.usuario.value?.id ?: return
        val foto = preferencias.obtener(id)?.fotoPerfilRuta
        supabase.postgrest.rpc("eliminar_cuenta")
        // Solo después de eliminarla en Supabase: si falla, la cuenta y sus datos siguen intactos.
        preferencias.borrar(id)
        preferencias.borrarCategorias(id)
        favoritos.borrarTodos(id)
        foto?.let(fotos::eliminar)
        sesion.cerrarSesion()
    }
}
