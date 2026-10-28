package com.cocinacapital.app.data.model

import org.junit.Assert.*
import org.junit.Test

/** HU-21 y HU-24: recomendaciones según gustos, actividad y ubicación. */
class RecomendadorTest {
    private val sushi = LocalGastronomico("s", "Sushi Muelle", "c-sushi", "Valparaíso", valoracion = 4.0,
        categoriaNombre = "Sushi", ticketPromedioPersona = 12_000, distanciaKm = 5.0)
    private val cafe = LocalGastronomico("c", "Café Cerro", "c-cafe", "Valparaíso", valoracion = 4.8,
        categoriaNombre = "Cafetería", ticketPromedioPersona = 5_000, distanciaKm = 5.0)
    private val burger = LocalGastronomico("b", "Burger Pacífico", "c-burger", "Viña del Mar", valoracion = 4.2,
        categoriaNombre = "Burgers", ticketPromedioPersona = 9_000, distanciaKm = 5.0)
    private val locales = listOf(sushi, cafe, burger)

    private fun ids(lista: List<LocalGastronomico>) = lista.map { it.id }

    @Test fun `sin datos de la persona se ordena por valoracion y cercania`() {
        val perfil = PerfilRecomendacion()
        assertFalse(perfil.personalizado)
        assertEquals(listOf("c", "b", "s"), ids(Recomendador.ordenarLocales(locales, perfil)))
        val cercaDelSushi = locales.map { if (it.id == "s") it.copy(distanciaKm = 0.2) else it.copy(distanciaKm = 19.0) }
        assertEquals("s", Recomendador.ordenarLocales(cercaDelSushi, perfil).first().id)
    }

    @Test fun `una cocina preferida pasa por delante de una mejor valorada`() {
        val perfil = PerfilRecomendacion(categoriasPreferidas = setOf("c-sushi"))
        assertEquals("s", Recomendador.ordenarLocales(locales, perfil).first().id)
    }

    @Test fun `las recomendaciones cambian al variar el historial`() {
        val antes = Recomendador.ordenarLocales(locales, PerfilRecomendacion())
        val conCanjes = PerfilRecomendacion(actividad = listOf(
            ActividadReciente("canje", "b"), ActividadReciente("vista_promocion", "b")
        ))
        val despues = Recomendador.ordenarLocales(locales, conCanjes)
        assertNotEquals(ids(antes), ids(despues))
        assertEquals("b", despues.first().id)
    }

    @Test fun `una busqueda cuenta para la cocina con ese nombre`() {
        val perfil = PerfilRecomendacion(actividad = listOf(ActividadReciente("busqueda", termino = "sushi")))
        assertEquals("s", Recomendador.ordenarLocales(locales, perfil).first().id)
    }

    @Test fun `el rango de precios desempata a favor de los locales acordes`() {
        val iguales = locales.map { it.copy(valoracion = 4.5) }
        val perfil = PerfilRecomendacion(rangoPrecio = RangoPrecio.ECONOMICO)
        assertEquals("c", Recomendador.ordenarLocales(iguales, perfil).first().id)
        assertTrue(RangoPrecio.MEDIO.incluyeTicket(12_000))
        assertFalse(RangoPrecio.ECONOMICO.incluyeTicket(12_000))
    }

    @Test fun `promociones para ti solo con datos de la persona y con afinidad`() {
        val promos = listOf(
            Promocion("p-s", "s", "Handroll 2x1", descuentoPorcentaje = 50, vigenteHasta = "2030-01-01T00:00:00Z"),
            Promocion("p-c", "c", "Café + kuchen", descuentoPorcentaje = 30, vigenteHasta = "2030-01-01T00:00:00Z"),
            Promocion("p-b", "b", "Combo tarde", descuentoPorcentaje = 25, vigenteHasta = "2030-01-01T00:00:00Z")
        )
        assertTrue(Recomendador.promocionesRecomendadas(promos, locales, PerfilRecomendacion()).isEmpty())
        val perfil = PerfilRecomendacion(categoriasPreferidas = setOf("c-sushi"))
        assertEquals(listOf("p-s"), Recomendador.promocionesRecomendadas(promos, locales, perfil).map { it.id })
    }

    @Test fun `actividad de locales que ya no estan se ignora sin fallar`() {
        val perfil = PerfilRecomendacion(actividad = listOf(ActividadReciente("canje", "no-existe"), ActividadReciente("desconocido", "s")))
        assertEquals(3, Recomendador.ordenarLocales(locales, perfil).size)
    }
}
