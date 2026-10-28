package com.cocinacapital.app.util

import java.text.Normalizer
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Locale chileno para formatear números (coma decimal). */
val LocaleCL: Locale = Locale.forLanguageTag("es-CL")

/** Distancia en km entre dos coordenadas (fórmula de Haversine). */
fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radioTierraKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * radioTierraKm * asin(sqrt(a))
}

/** Minúsculas y sin tildes, para que "cafeteria" encuentre "Cafetería". */
fun String.normalizado(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(LocaleCL)
        .trim()

/** Fecha de Supabase (ISO 8601) en hora de Santiago, ej: "3 de noviembre de 2026, 23:59"; `null` si no se entiende. */
fun fechaHoraChile(iso: String?): String? = iso?.let {
    runCatching {
        java.time.OffsetDateTime.parse(it)
            .atZoneSameInstant(java.time.ZoneId.of("America/Santiago"))
            .format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", LocaleCL))
    }.getOrNull()
}
