package com.cocinacapital.app.data.model

import java.time.Instant
import java.time.OffsetDateTime

enum class EstadoVigenciaPromocion {
    VIGENTE, PROGRAMADA, FINALIZADA, NO_DISPONIBLE, POR_CONFIRMAR, FUERA_DE_HORARIO, SIN_CUPOS
}

data class VigenciaPromocion(
    val desde: Instant?,
    val hasta: Instant?,
    val estado: EstadoVigenciaPromocion
)

/** El final es exclusivo: al llegar a vigente_hasta la promoción ya finalizó. */
fun Promocion.vigenciaEn(ahora: Instant): VigenciaPromocion {
    val desde = vigenteDesde.aInstante()
    val hasta = vigenteHasta.aInstante()
    val estado = when {
        !activa -> EstadoVigenciaPromocion.NO_DISPONIBLE
        hasta == null -> EstadoVigenciaPromocion.POR_CONFIRMAR
        desde != null && !desde.isBefore(hasta) -> EstadoVigenciaPromocion.POR_CONFIRMAR
        !ahora.isBefore(hasta) -> EstadoVigenciaPromocion.FINALIZADA
        desde == null -> EstadoVigenciaPromocion.POR_CONFIRMAR
        ahora.isBefore(desde) -> EstadoVigenciaPromocion.PROGRAMADA
        else -> EstadoVigenciaPromocion.VIGENTE
    }
    return VigenciaPromocion(desde, hasta, estado)
}

private fun String?.aInstante(): Instant? =
    this?.takeIf { it.isNotBlank() }?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
    }

fun Promocion.vigenciaParaDetalle(ahora: Instant, cuposConfigurados: Boolean): VigenciaPromocion {
    val vigencia = vigenciaEn(ahora)
    if (vigencia.estado != EstadoVigenciaPromocion.VIGENTE) return vigencia
    val cupos = cuposParaDetalle(cuposConfigurados)
    if (cupos is CuposPromocion.Disponibles && cupos.cantidad == 0)
        return vigencia.copy(estado = EstadoVigenciaPromocion.SIN_CUPOS)
    if (horarioPorConfirmar()) return vigencia.copy(estado = EstadoVigenciaPromocion.POR_CONFIRMAR)
    val local = ahora.atZone(java.time.ZoneId.of("America/Santiago"))
    val dias = diasParaDetalle()
    val franja = franjaParaDetalle()
    if (dias != null && dias.isNotEmpty() && dias.all { it in 1..7 } && local.dayOfWeek.value !in dias ||
        franja != null && (local.toLocalTime().isBefore(franja.desde) || !local.toLocalTime().isBefore(franja.hasta))) {
        return vigencia.copy(estado = EstadoVigenciaPromocion.FUERA_DE_HORARIO)
    }
    return vigencia
}
