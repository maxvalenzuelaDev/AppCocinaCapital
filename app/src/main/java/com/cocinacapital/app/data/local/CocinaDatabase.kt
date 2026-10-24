package com.cocinacapital.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos local (Room/SQLite, requisito RT-03 del ERS). */
@Database(
    entities = [PreferenciasEntity::class, CategoriaPreferidaEntity::class, FavoritoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CocinaDatabase : RoomDatabase() {
    abstract fun preferenciasDao(): PreferenciasDao
    abstract fun favoritosDao(): FavoritosDao

    companion object {
        fun crear(context: Context): CocinaDatabase =
            Room.databaseBuilder(context, CocinaDatabase::class.java, "cocina_capital.db").build()
    }
}
