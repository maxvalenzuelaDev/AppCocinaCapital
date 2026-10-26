package com.cocinacapital.app.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cocinacapital.app.data.location.*
import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.data.repository.HomeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Catalogo : HomeRepository {
        var falla = false
        var consultas = 0
        var locales = listOf(
            LocalGastronomico("00000000-0000-0000-0000-000000000001", "Café del Mar", "c1", "Viña del Mar", valoracion = 4.5,
                latitud = -33.0245, longitud = -71.5518, ticketPromedioPersona = 5000),
            LocalGastronomico("00000000-0000-0000-0000-000000000002", "Pizza Quinta", "c2", "Viña del Mar", valoracion = 4.8,
                latitud = -33.023, longitud = -71.551, ticketPromedioPersona = 17500)
        )
        override suspend fun getCategorias(): List<Categoria> { if (falla) error("sin red"); return listOf(Categoria("c1", "Cafetería"), Categoria("c2", "Pizzería")) }
        override suspend fun getLocales(): List<LocalGastronomico> { consultas++; return locales }
        override suspend fun getPromocionesVigentes() = emptyList<Promocion>()
    }
    private fun TestScope.watch(model: ExplorarViewModel) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} } }
    private fun gps() = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
    @Test fun searchRemainsEditableWhileTheFirstCatalogLoadIsPending() = runTest {
        val catalogo = Catalogo()
        val esperar = CompletableDeferred<Unit>()
        val lento = object : HomeRepository by catalogo {
            override suspend fun getLocales(): List<LocalGastronomico> { esperar.await(); return catalogo.getLocales() }
        }
        val vm = ExplorarViewModel(lento, gps(), flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); runCurrent()
        assertEquals(ExplorarUiState.Loading, vm.uiState.value)
        vm.elegirVina(); vm.iniciarBusquedaDesdeHome("c2"); vm.texto("pizza")
        assertEquals("pizza", vm.filtrosBusqueda.value.texto)
        assertEquals("c2", vm.filtrosBusqueda.value.categoriaId)
        esperar.complete(Unit); advanceUntilIdle()
        val state = vm.uiState.value as ExplorarUiState.Success
        assertEquals("pizza", state.filtros.texto)
        assertEquals(listOf("Pizza Quinta"), state.locales.map { it.nombre })
    }
    @Test fun catalogIdentifiersAreKeptForExistingDetailNavigation() = runTest {
        val catalogo = Catalogo(); val ubicacion = gps(); ubicacion.obtener()
        val vm = ExplorarViewModel(catalogo, ubicacion, flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); advanceUntilIdle()
        val state = vm.uiState.value as ExplorarUiState.Success
        assertEquals(catalogo.locales.map { it.id }, state.locales.map { it.id })
        vm.seleccionar(catalogo.locales[0].id); advanceUntilIdle()
        assertEquals(catalogo.locales[0].id, (vm.uiState.value as ExplorarUiState.Success).seleccionado?.id)
    }
    @Test fun noGpsMeansEmptyResultsUntilAManualZoneIsChosen() = runTest {
        val vm = ExplorarViewModel(Catalogo(), gps(), flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); advanceUntilIdle()
        assertTrue((vm.uiState.value as ExplorarUiState.Success).locales.isEmpty())
        vm.elegirVina(); advanceUntilIdle()
        assertFalse((vm.uiState.value as ExplorarUiState.Success).locales.isEmpty())
        vm.usarGps(); advanceUntilIdle()
        assertTrue((vm.uiState.value as ExplorarUiState.Success).locales.isEmpty())
    }
    @Test fun profileFavoriteCategoriesFilterBothTheMapAndTheList() = runTest {
        val ubicacion = gps(); ubicacion.obtener()
        val prefs = MutableStateFlow(PreferenciasUsuario())
        val vm = ExplorarViewModel(Catalogo(), ubicacion, prefs, SavedStateHandle())
        watch(vm); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ExplorarUiState.Success).locales.size)
        prefs.value = PreferenciasUsuario(categoriasIds = setOf("c2")); vm.preferidas(); advanceUntilIdle()
        assertEquals(listOf("c2"), (vm.uiState.value as ExplorarUiState.Success).locales.map { it.categoriaId })
    }
    @Test fun filtersAndSelectionSurviveViewModelRecreation() = runTest {
        val ubicacion = gps(); val saved = SavedStateHandle()
        val vm = ExplorarViewModel(Catalogo(), ubicacion, flowOf(PreferenciasUsuario()), saved)
        watch(vm); vm.elegirVina(); vm.texto("pizza"); vm.radio(10); vm.seleccionar("00000000-0000-0000-0000-000000000002"); advanceUntilIdle()
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        vm.viewModelScope.cancel()
        val next = ExplorarViewModel(Catalogo(), ubicacion, flowOf(PreferenciasUsuario()), restored)
        watch(next); advanceUntilIdle()
        val state = next.uiState.value as ExplorarUiState.Success
        assertEquals("pizza", state.filtros.texto); assertEquals(10, state.filtros.radioKm)
        assertEquals("Pizza Quinta", state.seleccionado?.nombre)
    }
    @Test fun failingCatalogCanBeRetried() = runTest {
        val catalogo = Catalogo().apply { falla = true }
        val vm = ExplorarViewModel(catalogo, gps(), flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); advanceUntilIdle(); assertEquals(ExplorarUiState.Error, vm.uiState.value)
        catalogo.falla = false; vm.recargar(); advanceUntilIdle()
        assertTrue(vm.uiState.value is ExplorarUiState.Success)
    }
    @Test fun notificationOptionsDoNotSilentlyRestrictExplore() = runTest {
        val ubicacion = gps(); ubicacion.obtener()
        val vm = ExplorarViewModel(Catalogo(), ubicacion, flowOf(PreferenciasUsuario(soloCategoriasFavoritas = true, categoriasIds = setOf("c2"))), SavedStateHandle())
        watch(vm); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ExplorarUiState.Success).locales.size)
    }
    @Test fun personBudgetUsesTheDeclaredAverageAndClearsThePreviousSelection() = runTest {
        val vm = ExplorarViewModel(Catalogo(), gps(), flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); vm.elegirVina(); advanceUntilIdle()
        vm.seleccionar("00000000-0000-0000-0000-000000000001")
        vm.presupuesto(PresupuestoPersona(15000, 20000)); advanceUntilIdle()
        val state = vm.uiState.value as ExplorarUiState.Success
        assertEquals(listOf("Pizza Quinta"), state.locales.map { it.nombre })
        assertEquals(17500L, state.locales.single().ticketPromedioPersona)
        assertNull(state.seleccionado)
    }
    @Test fun applyingAndClearingCombinedFiltersPreservesTheChosenOrigin() = runTest {
        val vm = ExplorarViewModel(Catalogo(), gps(), flowOf(PreferenciasUsuario(categoriasIds = setOf("c2"))), SavedStateHandle())
        watch(vm); vm.elegirVina(); advanceUntilIdle()
        vm.aplicarFiltros(FiltrosExplorar(texto = "pizza", categoriaId = "c2", radioKm = 20,
            soloPreferidas = true, presupuesto = PresupuestoPersona(15000, 20000))); advanceUntilIdle()
        val filtrado = vm.uiState.value as ExplorarUiState.Success
        assertEquals(listOf("Pizza Quinta"), filtrado.locales.map { it.nombre })
        assertTrue(filtrado.filtros.zonaManual)
        vm.limpiarFiltros(); advanceUntilIdle()
        val limpio = vm.uiState.value as ExplorarUiState.Success
        assertEquals(FiltrosExplorar(zonaManual = true), limpio.filtros)
        assertEquals(2, limpio.locales.size)
    }
    @Test fun customBudgetSurvivesSavedStateAndFilteringDoesNotReloadTheCatalog() = runTest {
        val saved = SavedStateHandle(); val catalogo = Catalogo(); val ubicacion = gps()
        val vm = ExplorarViewModel(catalogo, ubicacion, flowOf(PreferenciasUsuario()), saved)
        watch(vm); vm.elegirVina(); vm.presupuesto(PresupuestoPersona(4500, 125000)); advanceUntilIdle()
        vm.texto("cafe"); vm.radio(10); advanceUntilIdle()
        assertEquals(1, catalogo.consultas)
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        vm.viewModelScope.cancel()
        val next = ExplorarViewModel(catalogo, ubicacion, flowOf(PreferenciasUsuario()), restored)
        watch(next); advanceUntilIdle()
        val state = next.uiState.value as ExplorarUiState.Success
        assertEquals(PresupuestoPersona(4500, 125000), state.filtros.presupuesto)
        assertEquals(listOf("Café del Mar"), state.locales.map { it.nombre })
    }
    @Test fun unknownAveragesAreNotInventedAndUnboundedSearchKeepsThoseBusinesses() = runTest {
        val catalogo = Catalogo().apply { locales = locales.map { it.copy(ticketPromedioPersona = null) } }
        val vm = ExplorarViewModel(catalogo, gps(), flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); vm.elegirVina(); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ExplorarUiState.Success).locales.size)
        vm.presupuesto(PresupuestoPersona(maximo = 100000)); advanceUntilIdle()
        assertTrue((vm.uiState.value as ExplorarUiState.Success).locales.isEmpty())
        vm.presupuesto(null); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ExplorarUiState.Success).locales.size)
    }
    @Test fun obsoleteDishFilterIsNotMigratedToAPersonBudget() = runTest {
        val saved = SavedStateHandle(mapOf("precio" to "HASTA_5000", "manual" to true))
        val vm = ExplorarViewModel(Catalogo(), gps(), flowOf(PreferenciasUsuario()), saved)
        watch(vm); advanceUntilIdle()
        val state = vm.uiState.value as ExplorarUiState.Success
        assertNull(state.filtros.presupuesto)
        assertFalse(saved.contains("precio"))
        assertEquals(2, state.locales.size)
    }

    @Test fun openingSearchFromHomeKeepsTextAndAdvancedFiltersAndReplacesOnlyTheCategory() = runTest {
        val vm = ExplorarViewModel(Catalogo(), gps(), flowOf(PreferenciasUsuario(categoriasIds = setOf("c2"))), SavedStateHandle())
        watch(vm); vm.elegirVina()
        vm.aplicarFiltros(FiltrosExplorar(texto = "pizza", categoriaId = "c1", radioKm = 20,
            soloPreferidas = true, presupuesto = PresupuestoPersona(15000, 20000)))
        vm.seleccionar("anterior"); advanceUntilIdle()
        vm.iniciarBusquedaDesdeHome("c2"); advanceUntilIdle()
        val state = vm.uiState.value as ExplorarUiState.Success
        assertEquals("pizza", state.filtros.texto)
        assertEquals("c2", state.filtros.categoriaId)
        assertEquals(PresupuestoPersona(15000, 20000), state.filtros.presupuesto)
        assertEquals(20, state.filtros.radioKm)
        assertTrue(state.filtros.soloPreferidas && state.filtros.zonaManual)
        assertNull(state.filtros.seleccionadoId)
        assertEquals(listOf("Pizza Quinta"), state.locales.map { it.nombre })
    }

    @Test fun homeAllCategoriesDoesNotEraseTheExistingSearchOrBudget() = runTest {
        val vm = ExplorarViewModel(Catalogo(), gps(), flowOf(PreferenciasUsuario()), SavedStateHandle())
        watch(vm); vm.elegirVina(); vm.texto("cafe"); vm.categoria("c1"); vm.presupuesto(PresupuestoPersona(maximo = 10000))
        vm.iniciarBusquedaDesdeHome(null); advanceUntilIdle()
        val state = vm.uiState.value as ExplorarUiState.Success
        assertNull(state.filtros.categoriaId)
        assertEquals("cafe", state.filtros.texto)
        assertEquals(PresupuestoPersona(maximo = 10000), state.filtros.presupuesto)
        assertEquals(listOf("Café del Mar"), state.locales.map { it.nombre })
    }
}
