package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RutaRepositoryTest {
    private val request = SolicitudRuta("local", PuntoRuta(-33.01, -71.55), PuntoRuta(-33.02, -71.56), ModoRuta.A_PIE)
    private val json = """{"code":"Ok","routes":[{"distance":1400.0,"duration":900.0,"geometry":{"type":"LineString","coordinates":[[-71.55,-33.01],[-71.555,-33.018],[-71.56,-33.02]]}}]}"""

    @Test fun roadGeometryPreservesIntermediatePointsAndCoordinateOrder() {
        val route = interpretarRutaOsrm(json)
        assertEquals(3, route.puntos.size)
        assertEquals(PuntoRuta(-33.018, -71.555), route.puntos[1])
        assertEquals(1400.0, route.metros, 0.0)
        assertEquals(900.0, route.segundos, 0.0)
    }
    @Test fun noRouteAndNoSegmentAreHandledAsUnavailableRoutes() {
        for (code in listOf("NoRoute", "NoSegment")) {
            assertThrows(RutaNoEncontrada::class.java) { interpretarRutaOsrm("""{"code":"$code"}""") }
        }
    }
    @Test fun malformedOrImpossibleRoutesCannotBeDisplayed() {
        for (invalid in listOf(json.replace("1400.0", "-1"), json.replace("-33.018", "120"),
            json.replace("LineString", "Point"), """{"code":"Ok","routes":[]}""")) {
            assertThrows(Exception::class.java) { interpretarRutaOsrm(invalid) }
        }
    }
    @Test fun walkingAndDrivingUseTheirOwnServersAndLongitudeFirst() {
        val foot = urlRutaOsrm("https://routing.openstreetmap.de", request)
        assertTrue(foot.contains("/routed-foot/route/v1/foot/-71.55,-33.01;-71.56,-33.02"))
        val car = urlRutaOsrm("https://routing.openstreetmap.de", request.copy(modo = ModoRuta.AUTO))
        assertTrue(car.contains("/routed-car/route/v1/driving/"))
    }
    @Test fun repeatedSelectionUsesTheCacheUntilItExpires() = runTest {
        var downloads = 0
        val repo = OsrmRutaRepository({ downloads++; json }, clock = { testScheduler.currentTime })
        repo.calcular(request); repo.calcular(request)
        assertEquals(1, downloads)
        advanceTimeBy(120_001)
        repo.calcular(request)
        assertEquals(2, downloads)
    }
    @Test fun changingModesStillRespectsTheOneRequestPerSecondLimit() = runTest {
        val times = mutableListOf<Long>()
        val repo = OsrmRutaRepository({ times.add(testScheduler.currentTime); json }, clock = { testScheduler.currentTime })
        repo.calcular(request)
        repo.calcular(request.copy(modo = ModoRuta.AUTO))
        assertEquals(listOf(0L, 1_000L), times)
    }
    @Test fun sessionCleanupDiscardsLateResponsesAndTheirCache() = runTest {
        val answer = CompletableDeferred<String>()
        var downloads = 0
        val repo = OsrmRutaRepository({ downloads++; answer.await() }, clock = { testScheduler.currentTime })
        val pending = async { repo.calcular(request) }; runCurrent()
        repo.limpiar(); answer.complete(json)
        var cancelled = false
        try { pending.await() } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        repo.calcular(request)
        assertEquals(2, downloads)
    }
}
