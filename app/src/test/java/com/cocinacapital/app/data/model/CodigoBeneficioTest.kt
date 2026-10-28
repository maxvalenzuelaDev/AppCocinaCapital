package com.cocinacapital.app.data.model

import com.cocinacapital.app.data.repository.MotivoBilletera
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CodigoBeneficioTest {
    @Test fun `formatea el codigo en dos grupos y normaliza lo escrito a mano`() {
        assertEquals("ABCD-EFGH", CodigoBeneficio.formatear("abcdefgh"))
        assertEquals("ABCDEFGH", CodigoBeneficio.normalizar(" abcd-efgh "))
    }
    @Test fun `el QR lleva el prefijo y se lee de vuelta`() {
        val qr = CodigoBeneficio.contenidoQr("abcd-efgh")
        assertEquals("cocinacapital:beneficio:ABCDEFGH", qr)
        assertEquals("ABCDEFGH", CodigoBeneficio.desdeTexto(qr))
    }
    @Test fun `acepta el codigo escrito con guion espacios o minusculas`() {
        assertEquals("ABCDEFGH", CodigoBeneficio.desdeTexto("abcd efgh"))
        assertEquals("ABCDEFGH", CodigoBeneficio.desdeTexto("ABCD-EFGH"))
    }
    @Test fun `rechaza codigos incompletos y QR que no son de la app`() {
        assertNull(CodigoBeneficio.desdeTexto("ABC-123"))
        assertNull(CodigoBeneficio.desdeTexto("ABCDEFGHJ"))
        assertNull(CodigoBeneficio.desdeTexto("https://ejemplo.cl/ABCDEFGH"))
        assertNull(CodigoBeneficio.desdeTexto(""))
    }
    @Test fun `un beneficio vigente pasa a vencido al terminar la promocion`() {
        val beneficio = BeneficioUsuario(
            id = "b", codigo = "ABCDEFGH", estado = EstadoBeneficio.VIGENTE, guardadoEn = "2026-10-28T12:00:00Z",
            promocionId = "p", titulo = "Combo", descuentoPorcentaje = 20,
            vigenteHasta = "2026-10-30T03:00:00+00:00", localId = "l", localNombre = "Local"
        )
        assertEquals(EstadoBeneficio.VIGENTE, beneficio.estadoEn(Instant.parse("2026-10-30T02:59:59Z")))
        assertEquals(EstadoBeneficio.VENCIDO, beneficio.estadoEn(Instant.parse("2026-10-30T03:00:00Z")))
        val canjeado = beneficio.copy(estado = EstadoBeneficio.CANJEADO, canjeadoEn = "2026-10-29T12:00:00Z")
        assertEquals(EstadoBeneficio.CANJEADO, canjeado.estadoEn(Instant.parse("2026-11-01T00:00:00Z")))
    }
    @Test fun `solo se reconocen los codigos de error exactos de Supabase`() {
        assertEquals(MotivoBilletera.SIN_CUPOS, MotivoBilletera.desde("SIN_CUPOS"))
        assertEquals(MotivoBilletera.CODIGO_NO_ENCONTRADO, MotivoBilletera.desde(" CODIGO_NO_ENCONTRADO\n"))
        assertNull(MotivoBilletera.desde("permission denied for table beneficios_usuario"))
        assertNull(MotivoBilletera.desde(null))
    }
}
