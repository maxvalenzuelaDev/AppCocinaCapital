package com.cocinacapital.app.data.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DisponibilidadPromocionTest {
    private val promo = Promocion("p", "l", "Combo", descripcion = "Burger, papas y bebida entre 15:00 y 18:00.",
        descuentoPorcentaje = 25, vigenteDesde = "2026-10-01T00:00:00Z", vigenteHasta = "2026-11-01T00:00:00Z")
    @Test fun `recupera el horario explicito de la descripcion antigua`() {
        assertEquals("15:00", promo.franjaParaDetalle()!!.desde.toString())
        assertEquals("18:00", promo.franjaParaDetalle()!!.hasta.toString())
    }
    @Test fun `no inventa horas a partir de almuerzo ni de una descripcion vacia`() {
        assertNull(promo.copy(descripcion = "En horario de almuerzo").franjaParaDetalle())
        assertNull(promo.copy(descripcion = "").franjaParaDetalle())
    }
    @Test fun `horas estructuradas tienen prioridad y no se corrigen datos parciales con texto`() {
        assertEquals("14:00", promo.copy(horaInicio = "14:00", horaFin = "16:00").franjaParaDetalle()!!.desde.toString())
        assertNull(promo.copy(horaInicio = "14:00").franjaParaDetalle())
    }
    @Test fun `rangos ambiguos invalidos o invertidos no se interpretan`() {
        for (texto in listOf("Entre 25:00 y 28:00", "Entre 18:00 y 15:00", "De 10:00 a 12:00 y de 15:00 a 18:00"))
            assertNull(texto, promo.copy(descripcion = texto).franjaParaDetalle())
    }
    @Test fun `hora corta se normaliza sin cambiar su valor`() {
        assertEquals("09:00", promo.copy(descripcion = "De 9:00 a 11:00").franjaParaDetalle()!!.desde.toString())
    }
    @Test fun `recupera dias explicitos y mantiene prioridad del campo configurado`() {
        val antigua = promo.copy(descripcion = "Válido de lunes a jueves en horario de almuerzo.")
        assertEquals(listOf(1,2,3,4), antigua.diasParaDetalle())
        assertEquals(listOf(6), antigua.copy(diasSemana = listOf(6)).diasParaDetalle())
        assertNull(promo.diasParaDetalle())
    }
    @Test fun `un rango de dias puede cruzar el fin de semana`() {
        assertEquals(listOf(5,6,7,1), promo.copy(descripcion = "De viernes a lunes").diasParaDetalle())
    }
    @Test fun `el horario usa Santiago y limita inicio inclusivo y final exclusivo`() {
        assertEquals(EstadoVigenciaPromocion.FUERA_DE_HORARIO, promo.vigenciaParaDetalle(Instant.parse("2026-10-27T17:59:59Z"), true).estado)
        assertEquals(EstadoVigenciaPromocion.VIGENTE, promo.vigenciaParaDetalle(Instant.parse("2026-10-27T18:00:00Z"), true).estado)
        assertEquals(EstadoVigenciaPromocion.FUERA_DE_HORARIO, promo.vigenciaParaDetalle(Instant.parse("2026-10-27T21:00:00Z"), true).estado)
    }
    @Test fun `un dia no incluido no aparece disponible`() {
        assertEquals(EstadoVigenciaPromocion.FUERA_DE_HORARIO,
            promo.copy(diasSemana = listOf(1)).vigenciaParaDetalle(Instant.parse("2026-10-27T18:00:00Z"), true).estado)
    }
    @Test fun `cupos cero se muestran agotados pero null no equivale a cero`() {
        val ahora = Instant.parse("2026-10-27T18:00:00Z")
        assertEquals(EstadoVigenciaPromocion.SIN_CUPOS, promo.copy(cuposTotal = 10, cuposDisponibles = 0).vigenciaParaDetalle(ahora, true).estado)
        assertEquals(EstadoVigenciaPromocion.VIGENTE, promo.vigenciaParaDetalle(ahora, true).estado)
    }
    @Test fun `vigencia de fechas tiene prioridad sobre horarios y cupos`() {
        val sinCupos = promo.copy(cuposTotal = 10, cuposDisponibles = 0)
        assertEquals(EstadoVigenciaPromocion.FINALIZADA, sinCupos.vigenciaParaDetalle(Instant.parse("2026-11-02T18:00:00Z"), true).estado)
        assertEquals(EstadoVigenciaPromocion.NO_DISPONIBLE, sinCupos.copy(activa = false).vigenciaParaDetalle(Instant.parse("2026-10-27T18:00:00Z"), true).estado)
    }
    @Test fun `no interpreta excepciones ni notacion ambigua de doce horas como disponibilidad`() {
        for (texto in listOf("No válido entre 15:00 y 18:00", "De 3:00 a 6:00 pm", "Excepto de lunes a jueves")) {
            val dato = promo.copy(descripcion = texto)
            assertTrue(texto, dato.horarioPorConfirmar())
            assertEquals(EstadoVigenciaPromocion.POR_CONFIRMAR, dato.vigenciaParaDetalle(Instant.parse("2026-10-27T18:00:00Z"), true).estado)
        }
    }
}
