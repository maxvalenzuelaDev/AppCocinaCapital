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

/** Recupera solo una franja explícita de los registros anteriores a HU-27. No deduce horarios vagos. */
fun Promocion.franjaParaDetalle(): FranjaPromocion? {
    franjaValida()?.let { return it }
    if (horaInicio != null || horaFin != null) return null
    if (descripcion.restriccionAmbigua()) return null
    val coincidencias = Regex("(?:entre|de)\\s+(\\d{1,2}:\\d{2})\\s*(?:y|a|hasta|[–-])\\s*(\\d{1,2}:\\d{2})", RegexOption.IGNORE_CASE)
        .findAll(descripcion).toList()
    if (coincidencias.size != 1) return null
    val (desde, hasta) = coincidencias.single().destructured
    return copy(horaInicio = desde.padStart(5, '0'), horaFin = hasta.padStart(5, '0')).franjaValida()
}

fun Promocion.diasParaDetalle(): List<Int>? {
    diasSemana?.let { return it }
    if (descripcion.restriccionAmbigua()) return null
    val nombres = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    val nombresRegex = nombres.joinToString("|")
    val grupos = Regex("de (" + nombresRegex + ") a (" + nombresRegex + ")", RegexOption.IGNORE_CASE)
        .findAll(descripcion).toList()
    if (grupos.size != 1) return null
    val (inicio, fin) = grupos.single().destructured
    val primero = nombres.indexOf(inicio.lowercase(java.util.Locale.ROOT)) + 1
    val ultimo = nombres.indexOf(fin.lowercase(java.util.Locale.ROOT)) + 1
    return if (primero <= ultimo) (primero..ultimo).toList()
        else (primero..7).toList() + (1..ultimo).toList()
}


private fun String.restriccionAmbigua(): Boolean =
    Regex("""\b(no|excepto|exceptuando|salvo|am|pm)\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

fun Promocion.horarioPorConfirmar(): Boolean {
    if (diasSemana != null && (diasSemana.isEmpty() || diasSemana.any { it !in 1..7 })) return true
    if ((horaInicio != null || horaFin != null) && franjaValida() == null) return true
    if (horaInicio == null && horaFin == null && franjaParaDetalle() == null &&
        Regex("""\d{1,2}:\d{2}""").containsMatchIn(descripcion)) return true
    return diasSemana == null && diasParaDetalle() == null &&
        Regex("""\b(lunes|martes|miércoles|jueves|viernes|sábado|domingo)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(descripcion)
}
