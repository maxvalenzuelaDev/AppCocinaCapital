package com.cocinacapital.app.ui.home

import com.cocinacapital.app.data.location.FuenteUbicacionPrueba
import com.cocinacapital.app.data.location.AndroidUbicacionRepository
import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.location.LocationAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomePromocionesTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Catalogo : HomeRepository {
        var consultas = 0
        override suspend fun getCategorias(): List<Categoria> { consultas++; return listOf(Categoria("b", "Burgers"), Categoria("m", "Marina")) }
        override suspend fun getLocales(): List<LocalGastronomico> { consultas++; return listOf(
            LocalGastronomico("cerca", "Local cercano", "b", "Viña", latitud = -33.0245, longitud = -71.5518),
            LocalGastronomico("lejos", "Local lejano", "m", "Valparaíso", latitud = -33.0393, longitud = -71.6273),
            LocalGastronomico("sin-gps", "Local sin coordenadas", "b", "Viña"),
            LocalGastronomico("invalido", "Local inválido", "b", "Viña", latitud = 120.0, longitud = -71.55)
        ) }
        override suspend fun getPromocionesVigentes(): List<Promocion> {
            consultas++
            return listOf("sin-gps", "lejos", "cerca", "invalido", "no-disponible").map {
                Promocion("p-$it", it, "Oferta $it", descuentoPorcentaje = 20, vigenteHasta = "2099-01-01T00:00:00Z")
            }
        }
    }
    private fun TestScope.watch(vm: HomeViewModel) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    @Test fun withoutGpsUsesCatalogOrderAndDropsPromotionsOfUnavailableBusinesses() = runTest {
        val vm = HomeViewModel(Catalogo(), AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L })
        watch(vm); advanceUntilIdle()
        val state = vm.uiState.value as HomeUiState.Success
        assertFalse(state.tieneUbicacion)
        assertEquals(listOf("p-sin-gps", "p-lejos", "p-cerca", "p-invalido"), state.promociones.map { it.id })
        assertTrue(state.promociones.all { it.distanciaKm == null && it.localNombre.isNotBlank() })
    }
    @Test fun promotionsUseTheirBusinessDistanceAndNearestFirstWithUnknownDistancesLast() = runTest {
        val gps = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
        val vm = HomeViewModel(Catalogo(), gps); watch(vm); advanceUntilIdle()
        gps.obtener(); advanceUntilIdle()
        val state = vm.uiState.value as HomeUiState.Success
        assertTrue(state.tieneUbicacion)
        assertEquals(listOf("p-cerca", "p-lejos", "p-sin-gps", "p-invalido"), state.promociones.map { it.id })
        assertEquals(0.0, state.promociones.first().distanciaKm!!, .001)
        val distances = state.localesParaTi.associate { it.id to it.distanciaKm }
        state.promociones.forEach { assertEquals(distances[it.localId], it.distanciaKm) }
    }
    @Test fun losingPermissionRemovesPromotionDistancesAndRestoresTheGeneralFeed() = runTest {
        val fuente = FuenteUbicacionPrueba(); val gps = AndroidUbicacionRepository(fuente) { 100L }
        val vm = HomeViewModel(Catalogo(), gps); watch(vm); gps.obtener(); advanceUntilIdle()
        fuente.permiso = LocationAccess.NONE; gps.validarDisponibilidad(); advanceUntilIdle()
        val state = vm.uiState.value as HomeUiState.Success
        assertFalse(state.tieneUbicacion)
        assertTrue(state.promociones.all { it.distanciaKm == null })
        assertEquals("p-sin-gps", state.promociones.first().id)
    }
    @Test fun quickCategoriesStillFilterBothSectionsWithoutReloadingTheNetwork() = runTest {
        val catalogo = Catalogo(); val gps = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
        val vm = HomeViewModel(catalogo, gps); watch(vm); advanceUntilIdle()
        gps.obtener(); vm.onCategoriaClick("m"); advanceUntilIdle()
        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(listOf("lejos"), state.localesParaTi.map { it.id })
        assertEquals(listOf("p-lejos"), state.promociones.map { it.id })
        vm.onCategoriaClick("m"); advanceUntilIdle()
        assertNull((vm.uiState.value as HomeUiState.Success).categoriaSeleccionadaId)
        assertEquals(3, catalogo.consultas)
    }
}
