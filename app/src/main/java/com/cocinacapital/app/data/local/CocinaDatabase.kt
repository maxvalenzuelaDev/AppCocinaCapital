package com.cocinacapital.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base de datos local (Room/SQLite, requisito RT-03 del ERS). */
@Database(
    entities = [PreferenciasEntity::class, CategoriaPreferidaEntity::class, FavoritoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CocinaDatabase : RoomDatabase() {
    abstract fun preferenciasDao(): PreferenciasDao
    abstract fun favoritosDao(): FavoritosDao

    companion object {
        /** v2: foto de perfil (T-23). Conserva las preferencias y favoritos ya guardados. */
        private val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferencias_usuario ADD COLUMN fotoPerfilRuta TEXT")
            }
        }

        fun crear(context: Context): CocinaDatabase =
            Room.databaseBuilder(context, CocinaDatabase::class.java, "cocina_capital.db")
                .addMigrations(MIGRACION_1_2)
                .build()
    }
}
