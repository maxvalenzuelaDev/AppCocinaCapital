package com.cocinacapital.app.data.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** HU-22: qué promoción se avisa y cuándo no se avisa nada. */
class AvisoPromocionTest {
    // Martes 27 de octubre de 2026, 15:00 en Santiago (UTC−3).
    private val ahora = Instant.parse("2026-10-27T18:00:00Z")

    private val sushi = LocalGastronomico("s", "Sushi Muelle", "c-sushi", "Valparaíso", valoracion = 4.6, distanciaKm = 2.0)
    private val burger = LocalGastronomico("b", "Burger Pacífico", "c-burger", "Viña del Mar", valoracion = 4.2, distanciaKm = 9.0)
    private val locales = listOf(sushi, burger)

    private fun promo(id: String, local: String, pct: Int = 20, desde: String = "15:00", hasta: String = "18:00", dias: List<Int>? = null) =
        Promocion(id, local, "Promo $id", descuentoPorcentaje = pct,
            vigenteDesde = "2026-10-20T00:00:00Z", vigenteHasta = "2026-11-20T00:00:00Z",
            horaInicio = desde, horaFin = hasta, diasSemana = dias)

    private val promos = listOf(promo("ps", "s"), promo("pb", "b", pct = 50))

    @Test fun `avisa la promocion de una cocina preferida que se puede usar ahora`() {
        val prefs = PreferenciasUsuario(categoriasIds = setOf("c-burger"), notificaciones = true)
        assertEquals("pb", elegirPromocionParaAviso(promos, locales, prefs, ahora, emptySet())?.id)
    }

    @Test fun `sin cocinas preferidas ni filtro de cercania no avisa nada`() {
        assertNull(elegirPromocionParaAviso(promos, locales, PreferenciasUsuario(notificaciones = true), ahora, emptySet()))
    }

    @Test fun `no repite una promocion ya avisada`() {
        val prefs = PreferenciasUsuario(categoriasIds = setOf("c-burger", "c-sushi"))
        val primera = elegirPromocionParaAviso(promos, locales, prefs, ahora, emptySet())!!
        val segunda = elegirPromocionParaAviso(promos, locales, prefs, ahora, setOf(primera.id))!!
        assertNotEquals(primera.id, segunda.id)
        assertNull(elegirPromocionParaAviso(promos, locales, prefs, ahora, setOf("ps", "pb")))
    }

    @Test fun `fuera de su franja u otro dia no se avisa`() {
        val prefs = PreferenciasUsuario(categoriasIds = setOf("c-sushi"))
        assertNull(elegirPromocionParaAviso(listOf(promo("ps", "s", desde = "19:00", hasta = "22:00")), locales, prefs, ahora, emptySet()))
        assertNull(elegirPromocionParaAviso(listOf(promo("ps", "s", dias = listOf(6, 7))), locales, prefs, ahora, emptySet()))
        assertNotNull(elegirPromocionParaAviso(listOf(promo("ps", "s", dias = listOf(2))), locales, prefs, ahora, emptySet()))
    }

    @Test fun `solo cerca de mi descarta lo lejano y sirve aunque no haya cocinas preferidas`() {
        val cerca = PreferenciasUsuario(soloCercanas = true)
        assertEquals("ps", elegirPromocionParaAviso(promos, locales, cerca, ahora, emptySet())?.id)
        // El burger preferido está a 9 km: con "Solo cerca de mí" se avisa el sushi cercano, no el burger.
        val lejosYPreferida = PreferenciasUsuario(categoriasIds = setOf("c-burger"), soloCercanas = true)
        assertEquals("ps", elegirPromocionParaAviso(promos, locales, lejosYPreferida, ahora, emptySet())?.id)
        assertNull(elegirPromocionParaAviso(promos, locales, lejosYPreferida, ahora, setOf("ps")))
    }

    @Test fun `sin ubicacion solo cerca de mi no avisa`() {
        val sinDistancia = locales.map { it.copy(distanciaKm = null) }
        assertNull(elegirPromocionParaAviso(promos, sinDistancia, PreferenciasUsuario(soloCercanas = true), ahora, emptySet()))
    }

    @Test fun `solo de mis cocinas preferidas descarta lo cercano de otra cocina`() {
        val prefs = PreferenciasUsuario(categoriasIds = setOf("c-burger"), soloCercanas = false, soloCategoriasFavoritas = true)
        assertEquals("pb", elegirPromocionParaAviso(promos, locales, prefs, ahora, emptySet())?.id)
    }

    @Test fun `sin cupos no se avisa`() {
        val agotada = promo("ps", "s").copy(cuposTotal = 10, cuposDisponibles = 0)
        val prefs = PreferenciasUsuario(categoriasIds = setOf("c-sushi"))
        assertNull(elegirPromocionParaAviso(listOf(agotada), locales, prefs, ahora, emptySet()))
    }
}
