package com.cocinacapital.app.data.model

/**
 * Información que mostraremos en la billetera.
 *
 * Combina el beneficio guardado con los datos
 * reales de la promoción y el local.
 */
data class PromocionGuardada(

    // Registro de la tabla beneficios_usuario.
    val beneficio: BeneficioUsuario,

    // Información de la tabla promociones.
    val promocion: Promocion,

    // Nombre del local gastronómico.
    val nombreLocal: String
)
