package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.AuthGateway
import com.cocinacapital.app.data.local.FavoritoEntity
import com.cocinacapital.app.data.local.FavoritosDao
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.TipoFavorito
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Favoritos (locales y promociones) guardados en Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritosRepository(
    private val dao: FavoritosDao,
    private val auth: AuthGateway
) {
    private val userId: String get() = auth.usuario.value?.id ?: INVITADO

    fun observar(): Flow<List<Favorito>> = auth.usuario.flatMapLatest { usuario ->
        dao.observar(usuario?.id ?: INVITADO).map { lista -> lista.map { it.aDominio() } }
    }

    /** IDs favoritos de un tipo; el Home lo puede usar para pintar el corazón. */
    fun observarIds(tipo: TipoFavorito): Flow<Set<String>> =
        observar().map { lista -> lista.filter { it.tipo == tipo }.map { it.itemId }.toSet() }

    /** Si ya es favorito lo quita; si no, lo guarda. */
    suspend fun alternar(favorito: Favorito) {
        val id = userId
        if (dao.existe(id, favorito.itemId, favorito.tipo.name) > 0) {
            dao.eliminar(id, favorito.itemId, favorito.tipo.name)
        } else {
            dao.guardar(favorito.aEntidad(id))
        }
    }

    suspend fun eliminar(favorito: Favorito) = dao.eliminar(userId, favorito.itemId, favorito.tipo.name)

    private fun Favorito.aEntidad(userId: String) =
        FavoritoEntity(userId, itemId, tipo.name, nombre, subtitulo, imagenUrl)

    private fun FavoritoEntity.aDominio() = Favorito(
        itemId = itemId,
        tipo = runCatching { TipoFavorito.valueOf(tipo) }.getOrDefault(TipoFavorito.LOCAL),
        nombre = nombre,
        subtitulo = subtitulo,
        imagenUrl = imagenUrl
    )
}
