package com.cocinacapital.app.data.model

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

enum class RangoPrecio(val simbolo: String, val etiqueta: String) {
    ECONOMICO("\$", "Económico"),
    MEDIO("\$\$", "Medio"),
    PREMIUM("\$\$\$", "Premium")
}

enum class TemaApp(val etiqueta: String) {
    SISTEMA("Del sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro")
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
    val tema: TemaApp = TemaApp.SISTEMA
)

enum class TipoFavorito { LOCAL, PROMOCION }

data class Favorito(
    val itemId: String,
    val tipo: TipoFavorito,
    val nombre: String,
    val subtitulo: String,
    val imagenUrl: String? = null
)
