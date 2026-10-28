package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.PerfilSesion
import com.cocinacapital.app.data.local.CategoriaPreferidaEntity
import com.cocinacapital.app.data.local.PreferenciasDao
import com.cocinacapital.app.data.local.PreferenciasEntity
import com.cocinacapital.app.data.model.PreferenciasUsuario
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.OffsetDateTime

internal const val INVITADO = "invitado"

/**
 * Preferencias del usuario guardadas en Room y copiadas en Supabase (HU-02, T-06).
 *
 * Room es la fuente para la app: cada cambio se guarda al instante, queda marcado como pendiente y
 * se sube en [segundoPlano]. Al iniciar sesión, [sincronizar] sube lo pendiente o, si no hay nada,
 * descarga lo de Supabase; así un cambio hecho en un teléfono aparece en otro. Sin [remoto] (pruebas
 * o sin conexión configurada) funciona solo con Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenciasRepository(
    private val dao: PreferenciasDao,
    private val auth: PerfilSesion,
    private val remoto: PreferenciasRemotas? = null,
    private val segundoPlano: CoroutineScope? = null
) {
    private val userId: String get() = auth.usuario.value?.id ?: INVITADO

    /** Una subida o descarga a la vez: así no llegan a Supabase en desorden. */
    private val sincronizando = Mutex()

    /** Se reinicia solo si cambia el usuario (cierre/inicio de sesión). */
    fun observar(): Flow<PreferenciasUsuario> = auth.usuario.flatMapLatest { usuario ->
        val id = usuario?.id ?: INVITADO
        combine(dao.observar(id), dao.observarCategorias(id)) { prefs, categorias ->
            (prefs ?: PreferenciasEntity(userId = id)).aDominio(categorias.toSet())
        }
    }

    suspend fun alternarCategoria(categoriaId: String) {
        val id = userId
        if (categoriaId in dao.obtenerCategorias(id)) dao.quitarCategoria(id, categoriaId)
        else dao.agregarCategoria(CategoriaPreferidaEntity(id, categoriaId))
        modificar(id) { it }
    }

    suspend fun setRangoPrecio(rango: RangoPrecio) = modificar { it.copy(rangoPrecio = rango.name) }
    suspend fun setNotificaciones(activas: Boolean) = modificar { it.copy(notificaciones = activas) }
    suspend fun setSoloCercanas(valor: Boolean) = modificar { it.copy(soloCercanas = valor) }
    suspend fun setSoloCategoriasFavoritas(valor: Boolean) = modificar { it.copy(soloCategoriasFavoritas = valor) }
    suspend fun setTema(tema: TemaApp) = modificar { it.copy(tema = tema.name) }

    /** Guarda la fecha y hora de aceptación (RF-03); al revocar se borra. */
    suspend fun setConsentimiento(acepta: Boolean) = responderConsentimiento(userId, acepta)

    /**
     * Si [id] ya respondió la pantalla de consentimiento (HU-03). Recibe el usuario de la sesión
     * recién iniciada para no leer las preferencias de otro mientras la sesión termina de cargar.
     */
    fun consentimientoRespondido(id: String): Flow<Boolean> =
        dao.observar(id).map { it?.consentimientoRespondido == true }

    /** Respuesta de la pantalla del primer uso o del interruptor de Perfil; queda en el historial de Supabase. */
    suspend fun responderConsentimiento(id: String, acepta: Boolean) {
        modificar(id) {
            it.copy(
                consentimiento = acepta,
                consentimientoFecha = if (acepta) System.currentTimeMillis() else null,
                consentimientoRespondido = true
            )
        }
        enSegundoPlano(id) { it.registrarConsentimiento(acepta) }
    }

    /** Guarda la nueva ruta (o `null` para quitar la foto) y devuelve la anterior, para borrar su archivo. */
    suspend fun setFotoPerfil(ruta: String?): String? {
        val id = userId
        val anterior = dao.obtener(id)?.fotoPerfilRuta
        // La foto es un archivo del teléfono: no se sube, así que no deja nada pendiente.
        dao.guardar((dao.obtener(id) ?: PreferenciasEntity(userId = id)).copy(fotoPerfilRuta = ruta))
        return anterior
    }

    /**
     * Al iniciar sesión (T-06): si en este teléfono quedó algo sin subir, gana lo local y se sube;
     * si no, se descarga lo guardado en Supabase. Si falla la red, la app sigue con lo de Room.
     */
    suspend fun sincronizar(id: String) {
        val nube = remoto ?: return
        if (id == INVITADO) return
        if (dao.obtener(id)?.pendienteSincronizar == true) {
            subir(id)
            return
        }
        val guardadas = nube.obtener(id)
        if (guardadas == null) {
            // Primera vez de esta cuenta en Supabase: se sube lo que haya en el teléfono.
            if (dao.obtener(id) != null) {
                modificarLocal(id) { it.copy(pendienteSincronizar = true) }
                subir(id)
            }
            return
        }
        sincronizando.withLock {
            val actual = dao.obtener(id) ?: PreferenciasEntity(userId = id)
            // Si mientras se descargaba se cambió algo aquí, eso se sube en vez de pisarlo.
            if (actual.pendienteSincronizar) return
            dao.guardar(actual.conRemotas(guardadas))
            dao.borrarCategorias(id)
            guardadas.categorias.forEach { dao.agregarCategoria(CategoriaPreferidaEntity(id, it)) }
        }
    }

    /** Sube la fila de [id] si tiene cambios; solo la marca al día si no cambió mientras se subía. */
    private suspend fun subir(id: String) {
        val nube = remoto ?: return
        sincronizando.withLock {
            val local = dao.obtener(id) ?: return
            if (!local.pendienteSincronizar) return
            val categorias = dao.obtenerCategorias(id).toSet()
            nube.guardar(local.aRemotas(categorias))
            val despues = dao.obtener(id)
            if (despues == local && dao.obtenerCategorias(id).toSet() == categorias) {
                dao.guardar(local.copy(pendienteSincronizar = false))
            }
        }
    }

    private suspend fun modificar(id: String = userId, cambio: (PreferenciasEntity) -> PreferenciasEntity) {
        modificarLocal(id) { cambio(it).copy(pendienteSincronizar = true) }
        enSegundoPlano(id) { subir(id) }
    }

    private suspend fun modificarLocal(id: String, cambio: (PreferenciasEntity) -> PreferenciasEntity) {
        dao.guardar(cambio(dao.obtener(id) ?: PreferenciasEntity(userId = id)))
    }

    /** Sin conexión el cambio queda pendiente en Room y se sube al próximo cambio o inicio de sesión. */
    private fun enSegundoPlano(id: String, accion: suspend (PreferenciasRemotas) -> Unit) {
        val nube = remoto ?: return
        if (id == INVITADO) return
        segundoPlano?.launch { runCatching { accion(nube) } }
    }

    private fun PreferenciasEntity.aDominio(categorias: Set<String>) = PreferenciasUsuario(
        categoriasIds = categorias,
        rangoPrecio = runCatching { RangoPrecio.valueOf(rangoPrecio) }.getOrDefault(RangoPrecio.MEDIO),
        notificaciones = notificaciones,
        soloCercanas = soloCercanas,
        soloCategoriasFavoritas = soloCategoriasFavoritas,
        consentimiento = consentimiento,
        consentimientoFecha = consentimientoFecha,
        consentimientoRespondido = consentimientoRespondido,
        tema = runCatching { TemaApp.valueOf(tema) }.getOrDefault(TemaApp.SISTEMA),
        fotoPerfilRuta = fotoPerfilRuta
    )
}

internal fun PreferenciasEntity.aRemotas(categorias: Set<String>) = PreferenciasRemotasDto(
    usuarioId = userId,
    categorias = categorias.sorted(),
    rangoPrecio = rangoPrecio,
    notificaciones = notificaciones,
    soloCercanas = soloCercanas,
    soloCategoriasFavoritas = soloCategoriasFavoritas,
    tema = tema,
    consentimiento = consentimiento,
    consentimientoFecha = consentimientoFecha?.let { Instant.ofEpochMilli(it).toString() },
    consentimientoRespondido = consentimientoRespondido
)

/** Lo descargado reemplaza lo local, salvo la foto, que es un archivo de este teléfono. */
internal fun PreferenciasEntity.conRemotas(r: PreferenciasRemotasDto) = copy(
    rangoPrecio = r.rangoPrecio,
    notificaciones = r.notificaciones,
    soloCercanas = r.soloCercanas,
    soloCategoriasFavoritas = r.soloCategoriasFavoritas,
    tema = r.tema,
    consentimiento = r.consentimiento,
    consentimientoFecha = r.consentimientoFecha?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
    consentimientoRespondido = r.consentimientoRespondido,
    pendienteSincronizar = false
)
