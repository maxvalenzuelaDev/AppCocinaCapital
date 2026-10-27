package com.cocinacapital.app.ui.experiencias

import com.cocinacapital.app.data.model.Experiencia
import com.cocinacapital.app.data.repository.ExperienciasRepository
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExperienciasViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val experiencia = Experiencia("e", "l", "Menú de temporada", descripcion = "Tres tiempos", horario = "Viernes 19:00", precioReferencial = 35000)
    private class Repo : ExperienciasRepository {
        var lista: suspend () -> List<Experiencia> = { emptyList() }
        var detalle: suspend (String) -> Experiencia? = { null }
        var consultas = 0
        override suspend fun listar(): List<Experiencia> { consultas++; return lista() }
        override suspend fun getExperiencia(id: String): Experiencia? { consultas++; return detalle(id) }
    }
    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun `carga el listado sin modificar los datos publicados`() = runTest {
        val repo = Repo().apply { lista = { listOf(experiencia) } }
        val vm = ExperienciasViewModel(repo)
        assertEquals(ExperienciasUiState.Loading, vm.uiState.value)
        runCurrent()
        assertEquals(listOf(experiencia), (vm.uiState.value as ExperienciasUiState.Success).datos)
    }
    @Test fun `un catalogo vacio se diferencia de un error`() = runTest {
        val vm = ExperienciasViewModel(Repo()); runCurrent()
        assertTrue((vm.uiState.value as ExperienciasUiState.Success).datos.isEmpty())
    }
    @Test fun `un fallo no se disfraza de listado vacio y permite reintentar`() = runTest {
        val repo = Repo().apply { lista = { throw IOException() } }
        val vm = ExperienciasViewModel(repo); runCurrent()
        assertEquals(ExperienciasUiState.Error, vm.uiState.value)
        repo.lista = { listOf(experiencia) }; vm.onReintentar(); runCurrent()
        assertEquals(1, (vm.uiState.value as ExperienciasUiState.Success).datos.size)
    }
    @Test fun `una consulta suspendida indefinidamente termina en error recuperable`() = runTest {
        val repo = Repo().apply { lista = { awaitCancellation() } }
        val vm = ExperienciasViewModel(repo); runCurrent()
        advanceTimeBy(15000); runCurrent()
        assertEquals(ExperienciasUiState.Error, vm.uiState.value)
        repo.lista = { emptyList() }; vm.onReintentar(); runCurrent()
        assertTrue(vm.uiState.value is ExperienciasUiState.Success)
    }
    @Test fun `una respuesta antigua no sustituye al reintento reciente`() = runTest {
        val gate = CompletableDeferred<List<Experiencia>>()
        val repo = Repo().apply { lista = { withContext(NonCancellable) { gate.await() } } }
        val vm = ExperienciasViewModel(repo); runCurrent()
        repo.lista = { listOf(experiencia) }; vm.onReintentar(); runCurrent()
        gate.complete(emptyList()); runCurrent()
        assertEquals(listOf(experiencia), (vm.uiState.value as ExperienciasUiState.Success).datos)
    }
    @Test fun `una cancelacion interna de la consulta permite reintentar en vez de quedar cargando`() = runTest {
        val vm = ExperienciasViewModel(Repo().apply { lista = { throw CancellationException() } })
        runCurrent(); assertEquals(ExperienciasUiState.Error, vm.uiState.value)
    }
    @Test fun `detalle conserva horario y precio propios de la experiencia`() = runTest {
        val repo = Repo().apply { detalle = { id -> if (id == "e") experiencia else null } }
        val vm = DetalleExperienciaViewModel("e", repo); runCurrent()
        val data = (vm.uiState.value as ExperienciasUiState.Success).datos!!
        assertEquals("Viernes 19:00", data.horario)
        assertEquals(35000L, data.precioReferencial)
        assertEquals("Tres tiempos", data.descripcion)
    }
    @Test fun `detalle inexistente devuelve ausencia y no una experiencia inventada`() = runTest {
        val vm = DetalleExperienciaViewModel("ausente", Repo()); runCurrent()
        assertNull((vm.uiState.value as ExperienciasUiState.Success).datos)
    }
    @Test fun `id vacio no consulta el servidor`() = runTest {
        val repo = Repo(); val vm = DetalleExperienciaViewModel(" ", repo); runCurrent()
        assertNull((vm.uiState.value as ExperienciasUiState.Success).datos)
        assertEquals(0, repo.consultas)
    }
    @Test fun `detalle con informacion parcial no inventa precio ni horario`() = runTest {
        val vm = DetalleExperienciaViewModel("e", Repo().apply {
            detalle = { experiencia.copy(horario = null, precioReferencial = null) }
        }); runCurrent()
        val data = (vm.uiState.value as ExperienciasUiState.Success).datos!!
        assertNull(data.horario); assertNull(data.precioReferencial)
    }
    @Test fun `detalle tambien permite recuperarse de una espera agotada`() = runTest {
        val repo = Repo().apply { detalle = { awaitCancellation() } }
        val vm = DetalleExperienciaViewModel("e", repo); runCurrent()
        advanceTimeBy(15000); runCurrent()
        assertEquals(ExperienciasUiState.Error, vm.uiState.value)
        repo.detalle = { experiencia }; vm.onReintentar(); runCurrent()
        assertEquals(experiencia, (vm.uiState.value as ExperienciasUiState.Success).datos)
    }
}
