package com.cocinacapital.app.data.model

/** Presupuesto en CLP aplicado al consumo promedio por persona declarado por el comercio. */
data class PresupuestoPersona(val minimo: Long = 0, val maximo: Long? = null) {
    init {
        require(minimo >= 0)
        require(maximo == null || maximo >= minimo)
    }

    val sinLimite: Boolean get() = minimo == 0L && maximo == null
    fun incluye(consumo: Long): Boolean = consumo > 0 && consumo >= minimo && (maximo == null || consumo <= maximo)
}

enum class ErrorPresupuesto { IMPORTE_INVALIDO, ORDEN_INVALIDO }
data class EntradaPresupuesto(val presupuesto: PresupuestoPersona? = null, val error: ErrorPresupuesto? = null)

/** Un campo vacío deja ese extremo abierto. No se confunden importes inválidos con ausencia de límite. */
fun leerPresupuesto(desde: String, hasta: String): EntradaPresupuesto {
    fun importe(texto: String): Long? = texto.takeIf { it.all { c -> c in '0'..'9' } }?.toLongOrNull()
    val minText = desde.trim(); val maxText = hasta.trim()
    val min = if (minText.isEmpty()) 0L else importe(minText)
    val max = if (maxText.isEmpty()) null else importe(maxText)
    if (min == null || (maxText.isNotEmpty() && max == null)) return EntradaPresupuesto(error = ErrorPresupuesto.IMPORTE_INVALIDO)
    if (max != null && max < min) return EntradaPresupuesto(error = ErrorPresupuesto.ORDEN_INVALIDO)
    return EntradaPresupuesto(PresupuestoPersona(min, max).takeUnless { it.sinLimite })
}
