package com.cocinacapital.app.util

import com.cocinacapital.app.data.model.CodigoBeneficio
import org.junit.Assert.*
import org.junit.Test

class QrTest {
    @Test fun `el QR generado para un beneficio se puede leer de vuelta`() {
        val contenido = CodigoBeneficio.contenidoQr("K7MP2QXR")
        val matriz = generarQr(contenido, 300)
        assertEquals(300, matriz.width)
        assertEquals(contenido, leerQr(matriz.aPixeles(), matriz.width, matriz.height))
    }
    @Test fun `una imagen sin QR devuelve null en vez de fallar`() {
        val blanco = IntArray(200 * 200) { 0xFFFFFFFF.toInt() }
        assertNull(leerQr(blanco, 200, 200))
    }
}
