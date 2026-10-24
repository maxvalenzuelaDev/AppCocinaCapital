package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.PerfilSesion
import com.cocinacapital.app.data.local.CategoriaPreferidaEntity
import com.cocinacapital.app.data.local.PreferenciasDao
import com.cocinacapital.app.data.local.PreferenciasEntity
import com.cocinacapital.app.data.model.PreferenciasUsuario
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

internal const val INVITADO = "invitado"

/**
 * Preferencias del usuario guardadas en Room.
 * TODO(BD): sincronizar con Supabase (preferencias y registro de consentimientos con fecha).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenciasRepository(
    private val dao: PreferenciasDao,
    private val auth: PerfilSesion
) {
    private val userId: String get() = auth.usuario.value?.id ?: INVITADO

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
    }

    suspend fun setRangoPrecio(rango: RangoPrecio) = modificar { it.copy(rangoPrecio = rango.name) }
    suspend fun setNotificaciones(activas: Boolean) = modificar { it.copy(notificaciones = activas) }
    suspend fun setSoloCercanas(valor: Boolean) = modificar { it.copy(soloCercanas = valor) }
    suspend fun setSoloCategoriasFavoritas(valor: Boolean) = modificar { it.copy(soloCategoriasFavoritas = valor) }
    suspend fun setTema(tema: TemaApp) = modificar { it.copy(tema = tema.name) }

    /** Guarda la fecha y hora de aceptación (RF-03); al revocar se borra. */
    suspend fun setConsentimiento(acepta: Boolean) = modificar {
        it.copy(consentimiento = acepta, consentimientoFecha = if (acepta) System.currentTimeMillis() else null)
    }

    /** Guarda la nueva ruta (o `null` para quitar la foto) y devuelve la anterior, para borrar su archivo. */
    suspend fun setFotoPerfil(ruta: String?): String? {
        val anterior = dao.obtener(userId)?.fotoPerfilRuta
        modificar { it.copy(fotoPerfilRuta = ruta) }
        return anterior
    }

    private suspend fun modificar(cambio: (PreferenciasEntity) -> PreferenciasEntity) {
        val id = userId
        dao.guardar(cambio(dao.obtener(id) ?: PreferenciasEntity(userId = id)))
    }

    private fun PreferenciasEntity.aDominio(categorias: Set<String>) = PreferenciasUsuario(
        categoriasIds = categorias,
        rangoPrecio = runCatching { RangoPrecio.valueOf(rangoPrecio) }.getOrDefault(RangoPrecio.MEDIO),
        notificaciones = notificaciones,
        soloCercanas = soloCercanas,
        soloCategoriasFavoritas = soloCategoriasFavoritas,
        consentimiento = consentimiento,
        consentimientoFecha = consentimientoFecha,
        tema = runCatching { TemaApp.valueOf(tema) }.getOrDefault(TemaApp.SISTEMA),
        fotoPerfilRuta = fotoPerfilRuta
    )
}
