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

/**
 * Franja de uso. Con las columnas de HU-27 ([horarioConfigurado]) manda lo que guardó el oferente;
 * solo en bases anteriores se recupera una franja explícita de la descripción, sin deducir horarios vagos.
 */
fun Promocion.franjaParaDetalle(horarioConfigurado: Boolean = false): FranjaPromocion? {
    if (horarioConfigurado) return franjaValida()
    franjaValida()?.let { return it }
    if (horaInicio != null || horaFin != null) return null
    if (descripcion.restriccionAmbigua()) return null
    val coincidencias = Regex("(?:entre|de)\\s+(\\d{1,2}:\\d{2})\\s*(?:y|a|hasta|[–-])\\s*(\\d{1,2}:\\d{2})", RegexOption.IGNORE_CASE)
        .findAll(descripcion).toList()
    if (coincidencias.size != 1) return null
    val (desde, hasta) = coincidencias.single().destructured
    return copy(horaInicio = desde.padStart(5, '0'), horaFin = hasta.padStart(5, '0')).franjaValida()
}

/** Días de uso; igual que la franja, el texto solo se lee en bases sin las columnas de HU-27. */
fun Promocion.diasParaDetalle(horarioConfigurado: Boolean = false): List<Int>? {
    if (horarioConfigurado) return diasSemana
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


/**
 * Texto con excepciones o con horas de 12 h: no se interpreta. Un "no" suelto (por ejemplo "No acumulable")
 * no cuenta; solo los que niegan el horario ("no válido", "no aplica", "no rige", "no incluye").
 */
private fun String.restriccionAmbigua(): Boolean =
    Regex("""\b(excepto|exceptuando|salvo|am|pm)\b|\bno\s+(aplica|rige|incluye|es\s+v[aá]lid[oa]|v[aá]lid[oa])\b""", RegexOption.IGNORE_CASE)
        .containsMatchIn(this)

fun Promocion.horarioPorConfirmar(horarioConfigurado: Boolean = false): Boolean {
    if (diasSemana != null && (diasSemana.isEmpty() || diasSemana.any { it !in 1..7 })) return true
    if ((horaInicio != null || horaFin != null) && franjaValida() == null) return true
    // Con columnas de HU-27, NULL significa "todos los días" o "todo el horario": nada que confirmar.
    if (horarioConfigurado) return false
    if (horaInicio == null && horaFin == null && franjaParaDetalle() == null &&
        Regex("""\d{1,2}:\d{2}""").containsMatchIn(descripcion)) return true
    return diasSemana == null && diasParaDetalle() == null &&
        Regex("""\b(lunes|martes|miércoles|jueves|viernes|sábado|domingo)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(descripcion)
}
