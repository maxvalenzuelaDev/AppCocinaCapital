package com.cocinacapital.app.data.model

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VigenciaPromocionTest {
    private val ahora = Instant.parse("2026-10-27T15:00:00Z")
    private val promo = Promocion("p", "l", "Promoción", descuentoPorcentaje = 25,
        vigenteDesde = "2026-10-27T14:00:00Z", vigenteHasta = "2026-10-27T16:00:00Z")

    @Test fun `el inicio es inclusivo y el final exclusivo`() {
        assertEquals(EstadoVigenciaPromocion.VIGENTE, promo.vigenciaEn(Instant.parse(promo.vigenteDesde)).estado)
        assertEquals(EstadoVigenciaPromocion.FINALIZADA, promo.vigenciaEn(Instant.parse(promo.vigenteHasta)).estado)
    }
    @Test fun `no anuncia vigente una promocion que todavia no empieza`() {
        assertEquals(EstadoVigenciaPromocion.PROGRAMADA, promo.vigenciaEn(ahora.minusSeconds(7200)).estado)
    }
    @Test fun `una promocion pausada no se anuncia como vigente`() {
        assertEquals(EstadoVigenciaPromocion.NO_DISPONIBLE, promo.copy(activa = false).vigenciaEn(ahora).estado)
    }
    @Test fun `fechas ausentes invalidas o invertidas requieren confirmacion`() {
        val variantes = listOf(promo.copy(vigenteDesde = null), promo.copy(vigenteDesde = "desconocido"),
            promo.copy(vigenteHasta = ""), promo.copy(vigenteHasta = "mañana"),
            promo.copy(vigenteDesde = promo.vigenteHasta),
            promo.copy(vigenteDesde = "2026-10-28T14:00:00Z"))
        variantes.forEach { assertEquals(EstadoVigenciaPromocion.POR_CONFIRMAR, it.vigenciaEn(ahora).estado) }
    }
    @Test fun `los offsets distintos representan el mismo instante`() {
        val chile = promo.copy(vigenteDesde = "2026-10-27T11:00:00-03:00", vigenteHasta = "2026-10-27T13:00:00-03:00")
        assertEquals(promo.vigenciaEn(ahora), chile.vigenciaEn(ahora))
    }
    @Test fun `una fecha de final pasada basta para marcar finalizada aunque falte el inicio`() {
        assertEquals(EstadoVigenciaPromocion.FINALIZADA,
            promo.copy(vigenteDesde = null).vigenciaEn(Instant.parse(promo.vigenteHasta)).estado)
    }
    @Test fun `la base anterior no convierte cupos desconocidos en cero`() {
        val json = """{"id":"p","local_id":"l","titulo":"Oferta","descuento_porcentaje":25,"vigente_hasta":"2026-11-01T00:00:00Z"}"""
        val leida = Json.decodeFromString<Promocion>(json)
        assertTrue(leida.condiciones.isEmpty())
        assertNull(leida.horaInicio)
        assertNull(leida.horaFin)
        assertNull(leida.cuposDisponibles)
    }
    @Test fun `lee condiciones horario y cupos agotados sin perder campos del backend`() {
        val json = """{"id":"p","local_id":"l","titulo":"Oferta","descuento_porcentaje":25,"vigente_hasta":"2026-11-01T00:00:00Z","condiciones":"Solo consumo en local","dias_semana":[1,2,3,4],"hora_inicio":"15:00:00","hora_fin":"18:00:00","cupos_disponibles":0,"cupos_total":100}"""
        val leida = Json { ignoreUnknownKeys = true }.decodeFromString<Promocion>(json)
        assertEquals("Solo consumo en local", leida.condiciones)
        assertEquals(listOf(1,2,3,4), leida.diasSemana)
        assertEquals("15:00:00", leida.horaInicio)
        assertEquals("18:00:00", leida.horaFin)
        assertEquals(0, leida.cuposDisponibles)
    }
}
