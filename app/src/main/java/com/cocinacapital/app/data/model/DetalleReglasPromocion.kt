package com.cocinacapital.app.data.model

import java.time.LocalTime

sealed interface CuposPromocion {
    data object SinInformar : CuposPromocion
    data object SinLimite : CuposPromocion
    data class Disponibles(val cantidad: Int) : CuposPromocion
}

/** HU-27 define ambos campos NULL como sin límite; una base antigua no informa esa regla. */
fun Promocion.cuposParaDetalle(camposPresentes: Boolean): CuposPromocion {
    val disponibles = cuposDisponibles
    val total = cuposTotal
    if (disponibles != null && disponibles < 0 || total != null && total <= 0 ||
        total != null && disponibles != null && disponibles > total) return CuposPromocion.SinInformar
    if (disponibles != null) return CuposPromocion.Disponibles(disponibles)
    return if (total == null && camposPresentes) CuposPromocion.SinLimite else CuposPromocion.SinInformar
}

data class FranjaPromocion(val desde: LocalTime, val hasta: LocalTime)

fun Promocion.franjaValida(): FranjaPromocion? {
    val inicio = horaInicio?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val fin = horaFin?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    return FranjaPromocion(inicio, fin).takeIf { inicio.isBefore(fin) }
}
