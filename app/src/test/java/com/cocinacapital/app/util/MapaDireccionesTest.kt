package com.cocinacapital.app.util

import com.cocinacapital.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MapaDireccionesTest {
    @Test fun detailOpensDirectionsInsteadOfAPinSearch() {
        val url = urlDirecciones(PuntoRuta(-33.02, -71.56))
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
        assertTrue(url.contains("destination=-33.02,-71.56"))
        assertTrue(url.contains("dir_action=navigate"))
        assertFalse(url.contains("origin="))
    }
    @Test fun navigationKeepsTheRealOriginAndTheChosenTransport() {
        val url = urlDirecciones(PuntoRuta(-33.02, -71.56), PuntoRuta(-33.01, -71.55), ModoRuta.A_PIE)
        assertTrue(url.contains("origin=-33.01,-71.55"))
        assertTrue(url.contains("travelmode=walking"))
    }
}
