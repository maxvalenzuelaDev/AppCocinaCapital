package com.cocinacapital.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenciasDao {
    @Query("SELECT * FROM preferencias_usuario WHERE userId = :userId")
    fun observar(userId: String): Flow<PreferenciasEntity?>

    @Query("SELECT * FROM preferencias_usuario WHERE userId = :userId")
    suspend fun obtener(userId: String): PreferenciasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(preferencias: PreferenciasEntity)

    @Query("SELECT categoriaId FROM categorias_preferidas WHERE userId = :userId")
    fun observarCategorias(userId: String): Flow<List<String>>

    @Query("SELECT categoriaId FROM categorias_preferidas WHERE userId = :userId")
    suspend fun obtenerCategorias(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun agregarCategoria(categoria: CategoriaPreferidaEntity)

    @Query("DELETE FROM categorias_preferidas WHERE userId = :userId AND categoriaId = :categoriaId")
    suspend fun quitarCategoria(userId: String, categoriaId: String)

    @Query("DELETE FROM categorias_preferidas WHERE userId = :userId")
    suspend fun borrarCategorias(userId: String)

    /** Al eliminar la cuenta (T-07). */
    @Query("DELETE FROM preferencias_usuario WHERE userId = :userId")
    suspend fun borrar(userId: String)
}

@Dao
interface FavoritosDao {
    @Query("SELECT * FROM favoritos WHERE userId = :userId ORDER BY nombre")
    fun observar(userId: String): Flow<List<FavoritoEntity>>

    @Query("SELECT COUNT(*) FROM favoritos WHERE userId = :userId AND itemId = :itemId AND tipo = :tipo")
    suspend fun existe(userId: String, itemId: String, tipo: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(favorito: FavoritoEntity)

    @Query("DELETE FROM favoritos WHERE userId = :userId AND itemId = :itemId AND tipo = :tipo")
    suspend fun eliminar(userId: String, itemId: String, tipo: String)

    /** Al eliminar la cuenta (T-07). */
    @Query("DELETE FROM favoritos WHERE userId = :userId")
    suspend fun borrarTodos(userId: String)
}
