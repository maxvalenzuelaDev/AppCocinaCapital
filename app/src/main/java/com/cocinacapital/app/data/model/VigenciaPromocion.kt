package com.cocinacapital.app.data.model

import java.time.Instant
import java.time.OffsetDateTime

enum class EstadoVigenciaPromocion {
    VIGENTE, PROGRAMADA, FINALIZADA, NO_DISPONIBLE, POR_CONFIRMAR
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
