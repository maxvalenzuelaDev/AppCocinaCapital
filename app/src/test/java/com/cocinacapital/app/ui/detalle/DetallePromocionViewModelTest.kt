package com.cocinacapital.app.ui.detalle

import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.data.repository.DetallePromocionRepository
import com.cocinacapital.app.data.repository.PromocionDetalle
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetallePromocionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ahora = Instant.parse("2026-10-27T15:00:00Z")
    private val promo = Promocion("p", "l", "Oferta", descuentoPorcentaje = 25,
        vigenteDesde = "2026-10-27T14:00:00Z", vigenteHasta = "2026-10-27T16:00:00Z",
        condiciones = "Consumo en local", horaInicio = "15:00:00", horaFin = "18:00:00", cuposTotal = 7, cuposDisponibles = 7)
    private val local = LocalGastronomico("l", "Local", "c", "Viña del Mar", horario = "12:00–23:00")
    private class Reloj(var ahora: Instant) : Clock() {
        override fun instant() = ahora
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
    private class Repo : DetallePromocionRepository {
        var consultas = 0
        var consultasLocal = 0
        var promocion: suspend () -> Promocion? = { null }
        var local: suspend () -> LocalGastronomico? = { null }
        override suspend fun getPromocion(id: String): PromocionDetalle? {
            consultas++
            return promocion()?.let { PromocionDetalle(it, cuposConfigurados = true, horarioConfigurado = true) }
        }
        override suspend fun getLocal(localId: String): LocalGastronomico? { consultasLocal++; return local() }
    }
    private fun repo() = Repo().apply { promocion = { promo }; local = { this@DetallePromocionViewModelTest.local } }
    private fun vm(repo: Repo, id: String = "p", reloj: Clock = Clock.fixed(ahora, ZoneOffset.UTC)) =
        DetallePromocionViewModel(id, repo, reloj)

    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun `carga promocion y su local sin confundir sus horarios`() = runTest {
        val repo = repo(); val vm = vm(repo)
        assertEquals(DetallePromocionUiState.Loading, vm.uiState.value)
        runCurrent()
        val state = vm.uiState.value as DetallePromocionUiState.Success
        assertEquals(promo, state.promocion)
        assertEquals(local, state.local)
        assertEquals("15:00:00", state.promocion.horaInicio)
        assertEquals("18:00:00", state.promocion.horaFin)
        assertEquals("12:00–23:00", state.local!!.horario)
        assertEquals(EstadoVigenciaPromocion.VIGENTE, state.vigencia.estado)
        assertFalse(state.errorLocal)
    }
    @Test fun `id vacio no consulta el backend`() = runTest {
        val repo = repo(); val vm = vm(repo, " ")
        runCurrent()
        assertEquals(DetallePromocionUiState.NoEncontrada, vm.uiState.value)
        assertEquals(0, repo.consultas)
    }
    @Test fun `promocion inexistente no consulta un local`() = runTest {
        val repo = repo().apply { promocion = { null } }; val vm = vm(repo)
        runCurrent()
        assertEquals(DetallePromocionUiState.NoEncontrada, vm.uiState.value)
        assertEquals(0, repo.consultasLocal)
    }
    @Test fun `un fallo de red puede reintentarse`() = runTest {
        val repo = repo().apply { promocion = { throw IOException() } }; val vm = vm(repo)
        runCurrent(); assertEquals(DetallePromocionUiState.Error, vm.uiState.value)
        repo.promocion = { promo }; vm.onReintentar(); runCurrent()
        assertTrue(vm.uiState.value is DetallePromocionUiState.Success)
    }
    @Test fun `el fallo al cargar el local conserva la informacion de la promocion`() = runTest {
        val repo = repo().apply { local = { throw IOException() } }; val vm = vm(repo)
        runCurrent()
        val partial = vm.uiState.value as DetallePromocionUiState.Success
        assertTrue(partial.errorLocal); assertNull(partial.local); assertEquals(promo, partial.promocion)
        repo.local = { local }; vm.onReintentar(); runCurrent()
        assertFalse((vm.uiState.value as DetallePromocionUiState.Success).errorLocal)
    }
    @Test fun `no sustituye un local ausente por otro que no corresponde`() = runTest {
        val repo = repo().apply { local = { this@DetallePromocionViewModelTest.local.copy(id = "otro") } }
        val vm = vm(repo); runCurrent()
        val state = vm.uiState.value as DetallePromocionUiState.Success
        assertNull(state.local); assertFalse(state.errorLocal); assertEquals(promo, state.promocion)
    }
    @Test fun `el reintento mas nuevo no es reemplazado por una respuesta anterior`() = runTest {
        val gate = CompletableDeferred<Promocion>()
        val repo = repo().apply { promocion = { withContext(NonCancellable) { gate.await() } } }
        val vm = vm(repo); runCurrent()
        repo.promocion = { promo.copy(cuposDisponibles = 3) }
        vm.onReintentar(); runCurrent()
        gate.complete(promo); runCurrent()
        assertEquals(3, (vm.uiState.value as DetallePromocionUiState.Success).promocion.cuposDisponibles)
    }
    @Test fun `el reloj actualiza la vigencia sin otra consulta de red`() = runTest {
        val repo = repo(); val reloj = Reloj(ahora); val vm = vm(repo, reloj = reloj)
        runCurrent()
        reloj.ahora = Instant.parse(promo.vigenteHasta)
        vm.actualizarVigencia()
        assertEquals(EstadoVigenciaPromocion.FINALIZADA, (vm.uiState.value as DetallePromocionUiState.Success).vigencia.estado)
        assertEquals(1, repo.consultas)
    }
    @Test fun `la cancelacion no se convierte en un error para la interfaz`() = runTest {
        val repo = repo().apply { promocion = { throw CancellationException() } }
        val vm = vm(repo); runCurrent()
        assertEquals(DetallePromocionUiState.Loading, vm.uiState.value)
    }
    @Test fun `los datos no informados permanecen ausentes`() = runTest {
        val repo = repo().apply { promocion = { promo.copy(condiciones = "", horaInicio = null, horaFin = null, cuposTotal = null, cuposDisponibles = null) } }
        val vm = vm(repo); runCurrent()
        val data = (vm.uiState.value as DetallePromocionUiState.Success).promocion
        assertTrue(data.condiciones.isEmpty()); assertNull(data.horaInicio); assertNull(data.horaFin); assertNull(data.cuposDisponibles)
    }
}
