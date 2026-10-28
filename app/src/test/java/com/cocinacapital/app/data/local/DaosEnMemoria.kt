package com.cocinacapital.app.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * DAOs de Room en memoria para pruebas locales (T-17). Reproducen las consultas de `Daos.kt`
 * (filtran por usuario, ordenan favoritos por nombre, IGNORE/REPLACE) sin necesitar un dispositivo.
 */
class PreferenciasDaoEnMemoria : PreferenciasDao {
    val filas = MutableStateFlow<Map<String, PreferenciasEntity>>(emptyMap())
    val categorias = MutableStateFlow<Set<CategoriaPreferidaEntity>>(emptySet())

    override fun observar(userId: String): Flow<PreferenciasEntity?> = filas.map { it[userId] }
    override suspend fun obtener(userId: String) = filas.value[userId]
    override suspend fun guardar(preferencias: PreferenciasEntity) {
        filas.value = filas.value + (preferencias.userId to preferencias) // REPLACE
    }

    override fun observarCategorias(userId: String): Flow<List<String>> =
        categorias.map { todas -> todas.filter { it.userId == userId }.map { it.categoriaId } }
    override suspend fun obtenerCategorias(userId: String) =
        categorias.value.filter { it.userId == userId }.map { it.categoriaId }
    override suspend fun agregarCategoria(categoria: CategoriaPreferidaEntity) {
        categorias.value = categorias.value + categoria // IGNORE: un Set no duplica
    }
    override suspend fun quitarCategoria(userId: String, categoriaId: String) {
        categorias.value = categorias.value - CategoriaPreferidaEntity(userId, categoriaId)
    }
    override suspend fun borrarCategorias(userId: String) {
        categorias.value = categorias.value.filterNot { it.userId == userId }.toSet()
    }
    override suspend fun borrar(userId: String) {
        filas.value = filas.value - userId
    }
}

class FavoritosDaoEnMemoria : FavoritosDao {
    val filas = MutableStateFlow<List<FavoritoEntity>>(emptyList())

    override fun observar(userId: String): Flow<List<FavoritoEntity>> =
        filas.map { todos -> todos.filter { it.userId == userId }.sortedBy { it.nombre } }
    override suspend fun existe(userId: String, itemId: String, tipo: String) =
        filas.value.count { it.userId == userId && it.itemId == itemId && it.tipo == tipo }
    override suspend fun guardar(favorito: FavoritoEntity) {
        eliminar(favorito.userId, favorito.itemId, favorito.tipo) // REPLACE por clave primaria
        filas.value = filas.value + favorito
    }
    override suspend fun eliminar(userId: String, itemId: String, tipo: String) {
        filas.value = filas.value.filterNot { it.userId == userId && it.itemId == itemId && it.tipo == tipo }
    }
    override suspend fun borrarTodos(userId: String) {
        filas.value = filas.value.filterNot { it.userId == userId }
    }
}
