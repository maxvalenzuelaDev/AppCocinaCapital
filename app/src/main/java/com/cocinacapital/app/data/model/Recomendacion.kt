package com.cocinacapital.app.data.model

import com.cocinacapital.app.util.normalizado
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una fila reciente de `interacciones` (HU-20) que se usa para recomendar. */
@Serializable
data class ActividadReciente(
    val tipo: String,
    @SerialName("local_id") val localId: String? = null,
    val termino: String? = null
)

/**
 * Lo que se sabe de la persona para recomendarle (HU-21, HU-24): lo que eligió en Perfil y, solo si
 * dio su consentimiento (HU-03), su actividad reciente.
 */
data class PerfilRecomendacion(
    val categoriasPreferidas: Set<String> = emptySet(),
    val rangoPrecio: RangoPrecio? = null,
    val actividad: List<ActividadReciente> = emptyList()
) {
    /** Sin gustos ni actividad, "Para ti" sigue ordenando por valoración y cercanía. */
    val personalizado: Boolean get() = categoriasPreferidas.isNotEmpty() || actividad.isNotEmpty()
}

/** Ticket por persona (CLP) que encaja con cada rango del Perfil; los bordes se solapan a propósito. */
fun RangoPrecio.incluyeTicket(ticket: Long): Boolean = when (this) {
    RangoPrecio.ECONOMICO -> ticket <= 8_000
    RangoPrecio.MEDIO -> ticket in 6_000..15_000
    RangoPrecio.PREMIUM -> ticket >= 13_000
}

/**
 * Puntaje de recomendación (RF-14). Cada parte va de 0 a 1 y se pondera:
 * cocina preferida 30 %, interés por la cocina según la actividad 25 %, interés por ese mismo local
 * 10 %, precio acorde 10 %, valoración 15 % y cercanía 10 %. Sin datos de la persona solo quedan
 * valoración y cercanía, en la misma proporción que el orden anterior de "Para ti" (60/40).
 */
object Recomendador {
    private const val MAX_KM = 20.0
    private const val CANTIDAD_PROMOCIONES = 3

    /** Peso de cada tipo de interacción: un canje dice más que una vista. */
    private val PESO = mapOf("busqueda" to 1.0, "vista_local" to 1.0, "vista_promocion" to 1.5, "canje" to 3.0)

    /** Locales del más al menos recomendado. Necesita `distanciaKm` ya calculada (o `null`). */
    fun ordenarLocales(locales: List<LocalGastronomico>, perfil: PerfilRecomendacion): List<LocalGastronomico> {
        val intereses = Intereses.de(perfil.actividad, locales)
        return locales.sortedByDescending { puntaje(it, perfil, intereses) }
    }

    /**
     * Las promociones que más le convienen: las de sus locales recomendados, con un pequeño extra por
     * descuento. Vacía si no hay datos de la persona (no hay nada que personalizar).
     */
    fun promocionesRecomendadas(
        promociones: List<Promocion>,
        locales: List<LocalGastronomico>,
        perfil: PerfilRecomendacion
    ): List<Promocion> {
        if (!perfil.personalizado) return emptyList()
        val intereses = Intereses.de(perfil.actividad, locales)
        val localPorId = locales.associateBy { it.id }
        return promociones
            .mapNotNull { promo -> localPorId[promo.localId]?.let { promo to puntaje(it, perfil, intereses) } }
            .filter { (_, valor) -> valor > 0.35 } // solo las que tienen algo de afinidad, no cualquiera
            .sortedByDescending { (promo, valor) -> valor + 0.15 * promo.descuentoPorcentaje / 100.0 }
            .take(CANTIDAD_PROMOCIONES)
            .map { it.first }
    }

    internal fun puntaje(local: LocalGastronomico, perfil: PerfilRecomendacion, intereses: Intereses): Double {
        val preferida = if (local.categoriaId in perfil.categoriasPreferidas) 1.0 else 0.0
        val precio = local.ticketPromedioPersona
            ?.let { ticket -> if (perfil.rangoPrecio?.incluyeTicket(ticket) == true) 1.0 else 0.0 } ?: 0.0
        val valoracion = (local.valoracion / 5.0).coerceIn(0.0, 1.0)
        val cercania = 1.0 - ((local.distanciaKm ?: MAX_KM) / MAX_KM).coerceIn(0.0, 1.0)
        return 0.30 * preferida +
            0.25 * intereses.categoria(local.categoriaId) +
            0.10 * intereses.local(local.id) +
            0.10 * precio +
            0.15 * valoracion +
            0.10 * cercania
    }

    /** Interés relativo (0 a 1) por cocina y por local según la actividad reciente. */
    internal class Intereses private constructor(
        private val porCategoria: Map<String, Double>,
        private val porLocal: Map<String, Double>
    ) {
        fun categoria(id: String) = porCategoria[id] ?: 0.0
        fun local(id: String) = porLocal[id] ?: 0.0

        companion object {
            fun de(actividad: List<ActividadReciente>, locales: List<LocalGastronomico>): Intereses {
                if (actividad.isEmpty()) return Intereses(emptyMap(), emptyMap())
                val localPorId = locales.associateBy { it.id }
                val categorias = mutableMapOf<String, Double>()
                val porLocal = mutableMapOf<String, Double>()
                for (item in actividad) {
                    val peso = PESO[item.tipo] ?: continue
                    val local = item.localId?.let(localPorId::get)
                    if (local != null) {
                        categorias.merge(local.categoriaId, peso, Double::plus)
                        porLocal.merge(local.id, peso, Double::plus)
                    }
                    // Una búsqueda como "sushi" cuenta para la cocina con ese nombre.
                    val termino = item.termino?.normalizado()?.takeIf { it.length >= 3 } ?: continue
                    locales.filter { it.categoriaNombre.normalizado().let { n -> n.isNotEmpty() && (termino in n || n in termino) } }
                        .map { it.categoriaId }.distinct()
                        .forEach { categorias.merge(it, peso, Double::plus) }
                }
                return Intereses(categorias.normalizar(), porLocal.normalizar())
            }

            private fun Map<String, Double>.normalizar(): Map<String, Double> {
                val maximo = values.maxOrNull()?.takeIf { it > 0 } ?: return emptyMap()
                return mapValues { it.value / maximo }
            }
        }
    }
}
