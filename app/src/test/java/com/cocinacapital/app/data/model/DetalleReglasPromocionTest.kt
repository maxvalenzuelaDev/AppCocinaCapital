package com.cocinacapital.app.data.model

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class DetalleReglasPromocionTest {
    private val promo = Promocion("p", "l", "Oferta", descuentoPorcentaje = 25, vigenteHasta = "2026-11-01T00:00:00Z")

    @Test fun `la ausencia de columnas no se confunde con cupos ilimitados`() {
        assertEquals(CuposPromocion.SinInformar, promo.cuposParaDetalle(false))
        assertEquals(CuposPromocion.SinLimite, promo.cuposParaDetalle(true))
    }
    @Test fun `cupos agotados y cupos disponibles conservan el valor real`() {
        assertEquals(CuposPromocion.Disponibles(0), promo.copy(cuposTotal = 10, cuposDisponibles = 0).cuposParaDetalle(true))
        assertEquals(CuposPromocion.Disponibles(7), promo.copy(cuposTotal = 10, cuposDisponibles = 7).cuposParaDetalle(true))
    }
    @Test fun `un limite sin conteo disponible queda sin informar`() {
        assertEquals(CuposPromocion.SinInformar, promo.copy(cuposTotal = 10).cuposParaDetalle(true))
    }
    @Test fun `conteos inconsistentes no se presentan como disponibilidad confirmada`() {
        listOf(promo.copy(cuposDisponibles = -1), promo.copy(cuposTotal = 0),
            promo.copy(cuposTotal = 10, cuposDisponibles = 11)).forEach {
            assertEquals(CuposPromocion.SinInformar, it.cuposParaDetalle(true))
        }
    }
    @Test fun `interpreta la franja de HU27 en formato de postgres`() {
        assertEquals(FranjaPromocion(LocalTime.of(15,0), LocalTime.of(18,0)),
            promo.copy(horaInicio = "15:00:00", horaFin = "18:00:00").franjaValida())
    }
    @Test fun `una franja incompleta invalida o invertida no se anuncia como valida`() {
        listOf(promo, promo.copy(horaInicio = "15:00"),
            promo.copy(horaInicio = "25:00", horaFin = "18:00"),
            promo.copy(horaInicio = "18:00", horaFin = "15:00")).forEach { assertNull(it.franjaValida()) }
    }
}
