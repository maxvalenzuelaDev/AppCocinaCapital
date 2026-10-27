package com.cocinacapital.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class ExperienciaTest {
    private val experiencia = Experiencia("e", "l", "Degustación")
    @Test fun `precio ausente o invalido no se convierte en gratis`() {
        assertNull(experiencia.precioReferencialTexto())
        assertNull(experiencia.copy(precioReferencial = -1).precioReferencialTexto())
    }
    @Test fun `precio cero se distingue de no informado`() {
        assertNotNull(experiencia.copy(precioReferencial = 0).precioReferencialTexto())
    }
    @Test fun `precio referencial admite importes mayores al rango del menu`() {
        val texto = experiencia.copy(precioReferencial = 150000).precioReferencialTexto()!!
        assertTrue(texto.contains("150.000"))
        assertFalse(texto.contains(",00"))
    }
    @Test fun `lee campos propios y local asociado desde la respuesta de Postgrest`() {
        val data = Json.decodeFromString<Experiencia>("""{"id":"e","local_id":"l","titulo":"Degustación","horario":"Sábado 20:00","precio_referencial":45000,"locales":{"id":"l","nombre":"Local","categoria_id":"c","comuna":"Viña del Mar"}}""")
        assertEquals("Sábado 20:00", data.horario)
        assertEquals(45000L, data.precioReferencial)
        assertEquals("Local", data.local!!.nombre)
    }
}
