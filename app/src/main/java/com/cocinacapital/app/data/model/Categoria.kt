package com.cocinacapital.app.data.model

import kotlinx.serialization.Serializable

/** Fila de la tabla `categorias` (tipo de cocina). */
@Serializable
data class Categoria(
    val id: String,
    val nombre: String
)
