package com.cocinacapital.app.data.model

import java.time.Instant

/**
 * Elige la promoción que vale la pena avisar ahora (HU-22), o `null` si ninguna. Reglas:
 * - Se puede usar en este momento: dentro de su vigencia, día y franja horaria, y con cupos.
 * - Es afín: de una cocina preferida o, si pidió "Solo cerca de mí", de un local cercano.
 * - Respeta los filtros de Perfil ("Solo de mis cocinas preferidas" y "Solo cerca de mí").
 * - Nunca repite una promoción ya avisada.
 * Entre las que quedan, gana la mejor recomendada ([Recomendador]) con un extra por descuento.
 * [locales] necesita `distanciaKm` calculada; sin ubicación, "Solo cerca de mí" no avisa nada.
 */
fun elegirPromocionParaAviso(
    promociones: List<Promocion>,
    locales: List<LocalGastronomico>,
    preferencias: PreferenciasUsuario,
    ahora: Instant,
    yaAvisadas: Set<String>
): Promocion? {
    val localPorId = locales.associateBy { it.id }
    val perfil = PerfilRecomendacion(preferencias.categoriasIds, preferencias.rangoPrecio)
    val intereses = Recomendador.Intereses.de(emptyList(), locales)
    return promociones
        .asSequence()
        .filter { it.id !in yaAvisadas }
        .mapNotNull { promo -> localPorId[promo.localId]?.let { promo to it } }
        .filter { (promo, _) ->
            promo.vigenciaParaDetalle(ahora, cuposConfigurados = true, horarioConfigurado = true).estado ==
                EstadoVigenciaPromocion.VIGENTE
        }
        .filter { (_, local) ->
            val preferida = local.categoriaId in preferencias.categoriasIds
            val cerca = (local.distanciaKm ?: Double.POSITIVE_INFINITY) <= DISTANCIA_CERCA_KM
            (!preferencias.soloCategoriasFavoritas || preferida) &&
                (!preferencias.soloCercanas || cerca) &&
                (preferida || (preferencias.soloCercanas && cerca))
        }
        .maxByOrNull { (promo, local) ->
            Recomendador.puntaje(local, perfil, intereses) + 0.15 * promo.descuentoPorcentaje / 100.0
        }
        ?.first
}

/** "Cerca" para los avisos: una caminata o un viaje corto. */
const val DISTANCIA_CERCA_KM = 5.0
