package com.cocinacapital.app.ui.home

import com.cocinacapital.app.data.location.*
import com.cocinacapital.app.data.repository.FakeHomeRepository
import com.cocinacapital.location.LocationAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeUbicacionTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun homeReactsToTheSharedGpsAndToPermissionLoss() = runTest {
        val source = FuenteUbicacionPrueba()
        val gps = AndroidUbicacionRepository(source) { 100L }
        val home = HomeViewModel(FakeHomeRepository(), gps)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.uiState.collect {} }
        advanceUntilIdle()
        assertTrue((home.uiState.value as HomeUiState.Success).localesParaTi.all { it.distanciaKm == null })
        gps.obtener(); advanceUntilIdle()
        assertTrue((home.uiState.value as HomeUiState.Success).localesParaTi.all { it.distanciaKm != null })
        source.permiso = LocationAccess.NONE; gps.validarDisponibilidad(); advanceUntilIdle()
        assertTrue((home.uiState.value as HomeUiState.Success).localesParaTi.all { it.distanciaKm == null })
    }
}
