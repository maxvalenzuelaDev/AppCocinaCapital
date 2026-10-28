package com.cocinacapital.app.ui.explore

import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.data.repository.*
import com.cocinacapital.app.ui.home.UbicacionUsuario
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RutaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val local = LocalGastronomico("uno", "Local", "c", "Viña", latitud = -33.02, longitud = -71.56)
    private val origin = UbicacionUsuario("", -33.01, -71.55)
    private val route = Ruta(listOf(PuntoRuta(-33.01, -71.55), PuntoRuta(-33.02, -71.56)), 1400.0, 900.0)
    private class Repo(val answer: suspend (SolicitudRuta) -> Ruta) : RutaRepository {
        val calls = mutableListOf<SolicitudRuta>()
        override suspend fun calcular(solicitud: SolicitudRuta): Ruta { calls.add(solicitud); return answer(solicitud) }
    }

    @Test fun selectingALocalDoesNotSendCoordinatesUntilTheUserRequestsTheRoute() = runTest {
        val repo = Repo { route }; val vm = RutaViewModel(repo)
        vm.configurar(local, origin, false); runCurrent()
        assertTrue(vm.estado.value is RutaUiState.Pendiente)
        assertTrue(repo.calls.isEmpty())
        vm.calcular(); runCurrent()
        assertTrue(vm.estado.value is RutaUiState.Success)
    }
    @Test fun aManualZoneIsNeverUsedAsTheUsersGpsOrigin() = runTest {
        val repo = Repo { route }; val vm = RutaViewModel(repo)
        vm.configurar(local, origin, true); vm.calcular(); runCurrent()
        assertTrue(vm.estado.value is RutaUiState.SinUbicacion)
        assertTrue(repo.calls.isEmpty())
        vm.configurar(local, null, false); vm.calcular(); runCurrent()
        assertTrue(repo.calls.isEmpty())
    }
    @Test fun clearingSelectionRemovesTheOldRoute() = runTest {
        val vm = RutaViewModel(Repo { route })
        vm.configurar(local, origin, false); vm.calcular(); runCurrent()
        vm.configurar(null, origin, false)
        assertTrue(vm.estado.value is RutaUiState.SinLocal)
        assertNull(vm.estado.value.ruta)
    }
    @Test fun aLateAnswerCannotReplaceTheNewDestination() = runTest {
        val gate = CompletableDeferred<Ruta>()
        val vm = RutaViewModel(Repo { if (it.localId == "uno") withContext(NonCancellable) { gate.await() } else route.copy(metros = 2500.0) })
        vm.configurar(local, origin, false); vm.calcular(); runCurrent()
        vm.configurar(local.copy(id = "dos"), origin, false); vm.calcular(); runCurrent()
        gate.complete(route); runCurrent()
        assertEquals("dos", vm.estado.value.solicitud?.localId)
        assertEquals(2500.0, vm.estado.value.ruta!!.metros, 0.0)
    }
    @Test fun changingTransportRecalculatesInsteadOfReusingTheWalkingRoute() = runTest {
        val repo = Repo { route }; val vm = RutaViewModel(repo)
        vm.configurar(local, origin, false); vm.calcular(); runCurrent()
        vm.modo(ModoRuta.AUTO)
        assertNull(vm.estado.value.ruta)
        runCurrent()
        assertEquals(listOf(ModoRuta.A_PIE, ModoRuta.AUTO), repo.calls.map { it.modo })
        assertTrue(vm.estado.value is RutaUiState.Success)
    }
    @Test fun anUnavailableRouteCanBeRetried() = runTest {
        var unavailable = true
        val vm = RutaViewModel(Repo { if (unavailable) throw RutaNoEncontrada() else route })
        vm.configurar(local, origin, false); vm.calcular(); runCurrent()
        assertTrue((vm.estado.value as RutaUiState.Error).sinRuta)
        unavailable = false; vm.calcular(); runCurrent()
        assertTrue(vm.estado.value is RutaUiState.Success)
    }
    @Test fun aNewGpsPositionInvalidatesTheOldPath() = runTest {
        val vm = RutaViewModel(Repo { route })
        vm.configurar(local, origin, false); vm.calcular(); runCurrent()
        vm.configurar(local, origin.copy(latitud = -33.012), false)
        assertNull(vm.estado.value.ruta)
        assertTrue(vm.estado.value is RutaUiState.Pendiente)
    }
    @Test fun aNetworkFailureIsAnErrorAndRetryKeepsTheDestinationAndMode() = runTest {
        var fail = true
        val vm = RutaViewModel(Repo { if (fail) throw java.io.IOException() else route })
        vm.configurar(local, origin, false); vm.modo(ModoRuta.AUTO); vm.calcular(); runCurrent()
        val error = vm.estado.value as RutaUiState.Error
        assertFalse(error.sinRuta)
        assertEquals("uno", error.localId)
        assertEquals(ModoRuta.AUTO, error.modo)
        fail = false; vm.calcular(); runCurrent()
        val success = vm.estado.value as RutaUiState.Success
        assertEquals(error.contexto, success.contexto)
        assertEquals(route, success.ruta)
    }

}
