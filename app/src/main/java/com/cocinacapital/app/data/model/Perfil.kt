package com.cocinacapital.app.data.model

import androidx.annotation.StringRes
import com.cocinacapital.app.R

/** Usuario autenticado (lo entrega el módulo de Login/Registro de los compañeros). */
data class PerfilUsuario(
    val id: String,
    val nombre: String,
    val correo: String,
    val fotoUrl: String? = null
) {
    /** Iniciales para el avatar, ej: "Ana Pérez" → "AP". */
    val iniciales: String
        get() = nombre.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

enum class RangoPrecio(val simbolo: String, @StringRes val etiquetaRes: Int) {
    ECONOMICO("\$", R.string.rango_economico),
    MEDIO("\$\$", R.string.rango_medio),
    PREMIUM("\$\$\$", R.string.rango_premium)
}

enum class TemaApp(@StringRes val etiquetaRes: Int) {
    SISTEMA(R.string.tema_sistema),
    CLARO(R.string.tema_claro),
    OSCURO(R.string.tema_oscuro)
}

/** Preferencias del usuario (guardadas localmente con Room). Todo es opt-in por defecto (privacidad por defecto). */
data class PreferenciasUsuario(
    val categoriasIds: Set<String> = emptySet(),
    val rangoPrecio: RangoPrecio = RangoPrecio.MEDIO,
    val notificaciones: Boolean = false,
    val soloCercanas: Boolean = false,
    val soloCategoriasFavoritas: Boolean = false,
    /** Consentimiento para registrar actividad y personalizar (Ley 21.719 / 19.628). */
    val consentimiento: Boolean = false,
    val consentimientoFecha: Long? = null,
    /** Ya respondió la pantalla de consentimiento del primer uso (HU-03). */
    val consentimientoRespondido: Boolean = false,
    val tema: TemaApp = TemaApp.SISTEMA,
    /** Foto elegida en el Perfil (archivo local); tiene prioridad sobre [PerfilUsuario.fotoUrl]. */
    val fotoPerfilRuta: String? = null
)

enum class TipoFavorito { LOCAL, PROMOCION }

data class Favorito(
    val itemId: String,
    val tipo: TipoFavorito,
    val nombre: String,
    val subtitulo: String,
    val imagenUrl: String? = null
)
