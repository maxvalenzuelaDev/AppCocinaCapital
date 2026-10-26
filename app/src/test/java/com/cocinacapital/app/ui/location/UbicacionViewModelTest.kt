package com.cocinacapital.app.ui.location

import com.cocinacapital.app.data.location.FuenteUbicacionPrueba
import com.cocinacapital.app.data.location.AndroidUbicacionRepository
import com.cocinacapital.location.LocationAccess
import com.cocinacapital.location.LocationProblem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun loadingRetainsThePreviousPositionAndFailureHasNoStalePosition() = runTest {
        val source = FuenteUbicacionPrueba()
        val model = UbicacionViewModel(AndroidUbicacionRepository(source) { 100L })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        assertEquals(UbicacionUiState.Initial, model.uiState.value)
        model.obtener(); runCurrent()
        val previous = (model.uiState.value as UbicacionUiState.Success).posicion
        source.muestras = emptyList()
        source.flujo = flow { awaitCancellation() }
        model.obtener(); runCurrent()
        assertEquals(previous, (model.uiState.value as UbicacionUiState.Loading).posicionAnterior)
        model.permisoDenegado(); runCurrent()
        assertEquals(UbicacionUiState.Error(LocationProblem.PERMISSION_REQUIRED), model.uiState.value)
    }

    @Test fun repeatedRequestsConfirmSuccessEvenWhenCoordinatesAreUnchanged() = runTest {
        val model = UbicacionViewModel(AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val results = mutableListOf<UbicacionUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.resultados.collect { results.add(it) } }
        model.obtener(); runCurrent()
        model.obtener(); runCurrent()
        assertEquals(2, results.size)
        assertTrue(results.all { it is UbicacionUiState.Success })
    }

    @Test fun leavingTheForegroundCancelsTheRequestWithoutASuccessMessage() = runTest {
        val source = FuenteUbicacionPrueba().apply { muestras = emptyList(); flujo = flow { awaitCancellation() } }
        val model = UbicacionViewModel(AndroidUbicacionRepository(source) { 100L })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val results = mutableListOf<UbicacionUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.resultados.collect { results.add(it) } }
        model.obtener(); runCurrent()
        assertTrue(model.uiState.value is UbicacionUiState.Loading)
        model.cancelar(); runCurrent()
        assertFalse(model.uiState.value is UbicacionUiState.Loading)
        assertTrue(results.isEmpty())
    }

    @Test fun permissionDenialAndRecoveryProduceTheirOwnFeedback() = runTest {
        val source = FuenteUbicacionPrueba().apply { permiso = LocationAccess.NONE }
        val model = UbicacionViewModel(AndroidUbicacionRepository(source) { 100L })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        val results = mutableListOf<UbicacionUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.resultados.collect { results.add(it) } }
        model.permisoDenegado(); runCurrent()
        source.permiso = LocationAccess.PRECISE
        model.obtener(); runCurrent()
        assertEquals(2, results.size)
        assertEquals(LocationProblem.PERMISSION_REQUIRED, (results.first() as UbicacionUiState.Error).problema)
        assertTrue(results.last() is UbicacionUiState.Success)
    }
}
