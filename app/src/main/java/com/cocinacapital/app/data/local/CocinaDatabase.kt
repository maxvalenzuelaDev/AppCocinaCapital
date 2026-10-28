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
    version = 4,
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

        /**
         * v3: si ya respondió la pantalla de consentimiento (HU-03). Quien ya lo había aceptado desde
         * Perfil no vuelve a verla.
         */
        private val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferencias_usuario ADD COLUMN consentimientoRespondido INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE preferencias_usuario SET consentimientoRespondido = 1 WHERE consentimiento = 1")
            }
        }

        /** v4: sincronización con Supabase (T-06). Lo ya guardado en el teléfono se sube la primera vez. */
        private val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferencias_usuario ADD COLUMN pendienteSincronizar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE preferencias_usuario SET pendienteSincronizar = 1 WHERE userId <> 'invitado'")
            }
        }

        fun crear(context: Context): CocinaDatabase =
            Room.databaseBuilder(context, CocinaDatabase::class.java, "cocina_capital.db")
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4)
                .build()
    }
}
