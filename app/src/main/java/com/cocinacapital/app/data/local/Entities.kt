package com.cocinacapital.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Una fila por usuario con sus ajustes. */
@Entity(tableName = "preferencias_usuario")
data class PreferenciasEntity(
    @PrimaryKey val userId: String,
    val rangoPrecio: String = "MEDIO",
    val notificaciones: Boolean = false,
    val soloCercanas: Boolean = false,
    val soloCategoriasFavoritas: Boolean = false,
    val consentimiento: Boolean = false,
    val consentimientoFecha: Long? = null,
    /** Respondió la pantalla de consentimiento (HU-03), aceptando o no; sin esto se le vuelve a mostrar. */
    val consentimientoRespondido: Boolean = false,
    val tema: String = "SISTEMA",
    /** Ruta absoluta de la foto de perfil dentro de la carpeta privada de la app. */
    val fotoPerfilRuta: String? = null,
    /** Hay cambios que aún no se suben a Supabase (T-06); al iniciar sesión se suben antes de descargar. */
    val pendienteSincronizar: Boolean = false
)

/** Categorías gastronómicas elegidas por el usuario (relación muchos a muchos simple). */
@Entity(tableName = "categorias_preferidas", primaryKeys = ["userId", "categoriaId"])
data class CategoriaPreferidaEntity(
    val userId: String,
    val categoriaId: String
)

@Entity(tableName = "favoritos", primaryKeys = ["userId", "itemId", "tipo"])
data class FavoritoEntity(
    val userId: String,
    val itemId: String,
    val tipo: String,
    val nombre: String,
    val subtitulo: String,
    val imagenUrl: String?
)
