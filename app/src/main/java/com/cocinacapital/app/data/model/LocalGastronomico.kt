package com.cocinacapital.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Fila de la tabla `locales`.
 *
 * Los campos [categoriaNombre] y [distanciaKm] NO existen en la base de datos:
 * se calculan en la app (por eso son @Transient y no se leen/escriben en Supabase).
 */
@Serializable
data class LocalGastronomico(
    val id: String,
    val nombre: String,
    @SerialName("categoria_id") val categoriaId: String,
    val comuna: String,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    val valoracion: Double = 0.0,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val descripcion: String = "",
    val direccion: String? = null,
    /** Texto libre, ej: "Lun a Jue 12:00–22:00 · Vie y Sáb 12:00–00:00". */
    val horario: String? = null,
    @Transient val categoriaNombre: String = "",
    @Transient val distanciaKm: Double? = null
)
