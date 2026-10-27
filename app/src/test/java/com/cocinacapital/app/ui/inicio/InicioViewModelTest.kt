package com.cocinacapital.app.ui.inicio

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }
    @Test fun `la pantalla puede dibujarse antes de completar la inicializacion`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = InicioViewModel { gate.await() }
        assertEquals(InicioUiState.Loading, vm.uiState.value)
        runCurrent(); assertEquals(InicioUiState.Loading, vm.uiState.value)
        gate.complete(Unit); runCurrent()
        assertEquals(InicioUiState.Success, vm.uiState.value)
    }
    @Test fun `un error de inicio permite reintentar`() = runTest {
        var falla = true
        val vm = InicioViewModel { if (falla) error("Fallo controlado") }
        runCurrent(); assertEquals(InicioUiState.Error, vm.uiState.value)
        falla = false; vm.reintentar(); runCurrent()
        assertEquals(InicioUiState.Success, vm.uiState.value)
    }
    @Test fun `una espera indefinida termina con opcion de reintento`() = runTest {
        val vm = InicioViewModel { awaitCancellation() }
        runCurrent(); advanceTimeBy(20000); runCurrent()
        assertEquals(InicioUiState.Error, vm.uiState.value)
    }
    @Test fun `pulsaciones repetidas no duplican la inicializacion`() = runTest {
        var llamadas = 0
        val gate = CompletableDeferred<Unit>()
        val vm = InicioViewModel { llamadas++; gate.await() }
        vm.reintentar(); runCurrent(); vm.reintentar(); runCurrent()
        assertEquals(1, llamadas)
        gate.complete(Unit); runCurrent()
        assertEquals(InicioUiState.Success, vm.uiState.value)
    }
}
