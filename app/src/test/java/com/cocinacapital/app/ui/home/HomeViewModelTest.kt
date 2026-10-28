package com.cocinacapital.app.ui.home

import com.cocinacapital.app.R
import com.cocinacapital.app.data.location.AndroidUbicacionRepository
import com.cocinacapital.app.data.location.FuenteUbicacionPrueba
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.FakeHomeRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.HistorialInteracciones
import com.cocinacapital.app.data.model.ActividadReciente
import com.cocinacapital.app.data.model.PreferenciasUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Estados de carga y filtro por categoría del Inicio, con los datos de `FakeHomeRepository` (T-17). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun sinGps() = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
    private fun TestScope.observar(vm: HomeViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private fun HomeViewModel.success() = uiState.value as HomeUiState.Success

    /** Falla hasta que se le indique lo contrario, para probar el error y el reintento. */
    private class CatalogoQueFalla : HomeRepository {
        var falla = true
        private val real = FakeHomeRepository()
        override suspend fun getCategorias(): List<Categoria> { if (falla) error("sin red"); return real.getCategorias() }
        override suspend fun getLocales(): List<LocalGastronomico> = real.getLocales()
        override suspend fun getPromocionesVigentes(): List<Promocion> = real.getPromocionesVigentes()
    }

    @Test fun `muestra cargando mientras llegan los datos y luego el catalogo`() = runTest {
        val vm = HomeViewModel(FakeHomeRepository(), sinGps())
        observar(vm)
        assertEquals(HomeUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        val state = vm.success()
        assertEquals(6, state.categorias.size)
        assertEquals(6, state.localesParaTi.size)
        assertEquals(4, state.promociones.size)
        assertNull(state.categoriaSeleccionadaId)
    }

    @Test fun `completa el nombre de la categoria del local y del local de la promocion`() = runTest {
        val vm = HomeViewModel(FakeHomeRepository(), sinGps()); observar(vm); advanceUntilIdle()
        val state = vm.success()
        assertEquals("Cafetería", state.localesParaTi.first { it.id == "l2" }.categoriaNombre)
        assertEquals("Café Cerro Alegre", state.promociones.first { it.id == "p2" }.localNombre)
    }

    @Test fun `sin ubicacion Para ti ordena por valoracion`() = runTest {
        val vm = HomeViewModel(FakeHomeRepository(), sinGps()); observar(vm); advanceUntilIdle()
        assertEquals(listOf("l6", "l1", "l5", "l2", "l4", "l3"), vm.success().localesParaTi.map { it.id })
    }

    @Test fun `elegir una categoria filtra locales y promociones y tocarla de nuevo la quita`() = runTest {
        val vm = HomeViewModel(FakeHomeRepository(), sinGps()); observar(vm); advanceUntilIdle()
        vm.onCategoriaClick("c2"); advanceUntilIdle()
        val filtrado = vm.success()
        assertEquals("c2", filtrado.categoriaSeleccionadaId)
        assertEquals(listOf("l2"), filtrado.localesParaTi.map { it.id })
        assertEquals(listOf("p2"), filtrado.promociones.map { it.id })

        vm.onCategoriaClick("c2"); advanceUntilIdle()
        assertNull(vm.success().categoriaSeleccionadaId)
        assertEquals(6, vm.success().localesParaTi.size)
    }

    @Test fun `una categoria sin promociones deja la seccion vacia sin ocultar sus locales`() = runTest {
        val vm = HomeViewModel(FakeHomeRepository(), sinGps()); observar(vm); advanceUntilIdle()
        vm.onCategoriaClick("c6"); advanceUntilIdle()
        assertEquals(listOf("l6"), vm.success().localesParaTi.map { it.id })
        assertTrue(vm.success().promociones.isEmpty())
    }

    @Test fun `si falla la carga muestra error y reintentar recupera el catalogo`() = runTest {
        val catalogo = CatalogoQueFalla()
        val vm = HomeViewModel(catalogo, sinGps()); observar(vm); advanceUntilIdle()
        assertEquals(HomeUiState.Error(R.string.home_error), vm.uiState.value)
        catalogo.falla = false
        vm.onReintentar(); advanceUntilIdle()
        assertEquals(6, vm.success().localesParaTi.size)
    }

    // ---- HU-21 y HU-24: recomendaciones ----

    @Test fun `una cocina preferida en Perfil sube al primer lugar de Para ti`() = runTest {
        val prefs = MutableStateFlow(PreferenciasUsuario())
        val vm = HomeViewModel(FakeHomeRepository(), sinGps(), prefs); observar(vm); advanceUntilIdle()
        assertEquals("l6", vm.success().localesParaTi.first().id)
        assertFalse(vm.success().personalizado)
        prefs.value = PreferenciasUsuario(categoriasIds = setOf("c3"))
        advanceUntilIdle()
        assertEquals("l3", vm.success().localesParaTi.first().id)
        assertTrue(vm.success().personalizado)
        assertTrue(vm.success().promocionesParaTi.all { it.localId == "l3" })
    }

    @Test fun `la actividad solo cambia las recomendaciones con consentimiento`() = runTest {
        val prefs = MutableStateFlow(PreferenciasUsuario(consentimiento = false))
        val historial = HistorialInteracciones { listOf(ActividadReciente("canje", "l3"), ActividadReciente("vista_local", "l3")) }
        val vm = HomeViewModel(FakeHomeRepository(), sinGps(), prefs, historial); observar(vm); advanceUntilIdle()
        assertEquals("l6", vm.success().localesParaTi.first().id)
        prefs.value = PreferenciasUsuario(consentimiento = true)
        advanceUntilIdle()
        assertEquals("l3", vm.success().localesParaTi.first().id)
    }

    @Test fun `las promociones para ti no se repiten en la lista general`() = runTest {
        val prefs = MutableStateFlow(PreferenciasUsuario(categoriasIds = setOf("c1")))
        val vm = HomeViewModel(FakeHomeRepository(), sinGps(), prefs); observar(vm); advanceUntilIdle()
        val state = vm.success()
        val recomendadas = state.promocionesParaTi.map { it.id }.toSet()
        assertTrue(recomendadas.isNotEmpty())
        assertTrue(state.promociones.none { it.id in recomendadas })
    }

    @Test fun `sin red el historial anterior se conserva`() = runTest {
        var falla = false
        val historial = HistorialInteracciones { if (falla) error("sin red") else listOf(ActividadReciente("canje", "l3")) }
        val vm = HomeViewModel(FakeHomeRepository(), sinGps(), flowOf(PreferenciasUsuario(consentimiento = true)), historial)
        observar(vm); advanceUntilIdle()
        falla = true
        vm.refrescarActividad(); advanceUntilIdle()
        assertEquals("l3", vm.success().localesParaTi.first().id)
    }

    // ---- HU-35: sin conexión ----

    @Test fun `al volver la conexion se recarga solo si habia fallado`() = runTest {
        val catalogo = CatalogoQueFalla()
        val reconexiones = MutableSharedFlow<Unit>()
        val vm = HomeViewModel(catalogo, sinGps(), reconexiones = reconexiones); observar(vm); advanceUntilIdle()
        assertIs<HomeUiState.Error>(vm.uiState.value)
        catalogo.falla = false
        reconexiones.emit(Unit); advanceUntilIdle()
        assertEquals(6, vm.success().localesParaTi.size)
    }
}
