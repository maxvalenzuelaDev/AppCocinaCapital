package com.cocinacapital.app.ui.billetera

import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.BeneficioObtenido
import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.EstadoBeneficio
import com.cocinacapital.app.data.repository.BilleteraException
import com.cocinacapital.app.data.repository.BilleteraRepository
import com.cocinacapital.app.data.repository.MotivoBilletera
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Repositorio en memoria para las pruebas de la billetera. */
class BilleteraFalsa : BilleteraRepository {
    var beneficios = mutableListOf<BeneficioUsuario>()
    var lista: suspend () -> List<BeneficioUsuario> = { beneficios.toList() }
    var adquirirCon: suspend (String) -> BeneficioObtenido = { BeneficioObtenido("nuevo", "K7MP2QXR", promocionId = it) }
    var existente: suspend (String) -> BeneficioObtenido? = { null }
    var liberarCon: suspend (String) -> Unit = { id -> beneficios.removeAll { it.id == id } }
    var adquiridos = 0
    override suspend fun misBeneficios() = lista()
    override suspend fun beneficioDe(promocionId: String) = existente(promocionId)
    override suspend fun adquirir(promocionId: String): BeneficioObtenido { adquiridos++; return adquirirCon(promocionId) }
    override suspend fun liberar(beneficioId: String) = liberarCon(beneficioId)
}

fun beneficio(id: String, estado: EstadoBeneficio = EstadoBeneficio.VIGENTE, hasta: String = "2026-11-01T03:00:00Z") =
    BeneficioUsuario(
        id = id, codigo = "ABCDEFGH", estado = estado, guardadoEn = "2026-10-28T12:00:00Z",
        canjeadoEn = if (estado == EstadoBeneficio.CANJEADO) "2026-10-28T15:00:00Z" else null,
        promocionId = "p$id", titulo = "Promo $id", descuentoPorcentaje = 20, vigenteHasta = hasta,
        localId = "l", localNombre = "Caleta del Puerto"
    )

@OptIn(ExperimentalCoroutinesApi::class)
class BilleteraViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val reloj = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC)
    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun `separa vigentes del historial de canjeados y vencidos`() = runTest {
        val repo = BilleteraFalsa().apply {
            beneficios = mutableListOf(
                beneficio("a"), beneficio("b", EstadoBeneficio.CANJEADO),
                beneficio("c", EstadoBeneficio.VENCIDO), beneficio("d", hasta = "2026-10-29T11:00:00Z")
            )
        }
        val vm = BilleteraViewModel(repo, reloj); runCurrent()
        val estado = vm.uiState.value as BilleteraUiState.Success
        assertEquals(listOf("a"), estado.vigentes(vm.ahora).map { it.id })
        // "d" llegó vigente pero su promoción ya terminó según el reloj.
        assertEquals(listOf("b", "c", "d"), estado.historial(vm.ahora).map { it.id })
    }

    @Test fun `un error de carga permite reintentar`() = runTest {
        val repo = BilleteraFalsa().apply { lista = { throw IOException() } }
        val vm = BilleteraViewModel(repo, reloj); runCurrent()
        assertEquals(BilleteraUiState.Error(R.string.billetera_error_cargar), vm.uiState.value)
        repo.lista = { listOf(beneficio("a")) }
        vm.cargar(); runCurrent()
        assertEquals(1, (vm.uiState.value as BilleteraUiState.Success).beneficios.size)
    }

    @Test fun `tras un error sin red se recarga sola al volver la conexion`() = runTest {
        val repo = BilleteraFalsa().apply { lista = { throw IOException() } }
        val reconexiones = MutableSharedFlow<Unit>()
        val vm = BilleteraViewModel(repo, reloj, reconexiones); runCurrent()
        assertTrue(vm.uiState.value is BilleteraUiState.Error)
        repo.lista = { listOf(beneficio("a")) }
        reconexiones.emit(Unit); runCurrent()
        assertEquals(1, (vm.uiState.value as BilleteraUiState.Success).beneficios.size)
    }

    @Test fun `refrescar con la lista visible no la oculta si falla`() = runTest {
        val repo = BilleteraFalsa().apply { beneficios = mutableListOf(beneficio("a")) }
        val vm = BilleteraViewModel(repo, reloj); runCurrent()
        repo.lista = { throw IOException() }
        vm.refrescar(); runCurrent()
        assertEquals(1, (vm.uiState.value as BilleteraUiState.Success).beneficios.size)
        assertEquals(R.string.billetera_error_cargar, vm.mensajes.first())
    }

    @Test fun `quitar pide confirmacion y luego libera el beneficio`() = runTest {
        val repo = BilleteraFalsa().apply { beneficios = mutableListOf(beneficio("a"), beneficio("b")) }
        val vm = BilleteraViewModel(repo, reloj); runCurrent()
        val a = (vm.uiState.value as BilleteraUiState.Success).beneficios.first()
        vm.onPedirQuitar(a)
        assertEquals(a, (vm.uiState.value as BilleteraUiState.Success).porQuitar)
        vm.onConfirmarQuitar(); runCurrent()
        val estado = vm.uiState.value as BilleteraUiState.Success
        assertEquals(listOf("b"), estado.beneficios.map { it.id })
        assertNull(estado.porQuitar); assertNull(estado.quitandoId)
        assertEquals(R.string.billetera_quitado, vm.mensajes.first())
    }

    @Test fun `si no se pudo quitar porque ya se canjeo se recarga el estado real`() = runTest {
        val repo = BilleteraFalsa().apply { beneficios = mutableListOf(beneficio("a")) }
        val vm = BilleteraViewModel(repo, reloj); runCurrent()
        repo.liberarCon = { throw BilleteraException(MotivoBilletera.BENEFICIO_CANJEADO) }
        repo.beneficios = mutableListOf(beneficio("a", EstadoBeneficio.CANJEADO))
        vm.onPedirQuitar((vm.uiState.value as BilleteraUiState.Success).beneficios.first())
        vm.onConfirmarQuitar(); runCurrent()
        assertEquals(R.string.billetera_error_quitar, vm.mensajes.first())
        assertEquals(EstadoBeneficio.CANJEADO, (vm.uiState.value as BilleteraUiState.Success).beneficios.single().estado)
    }

    @Test fun `el codigo abierto se cierra si el beneficio ya no esta al recargar`() = runTest {
        val repo = BilleteraFalsa().apply { beneficios = mutableListOf(beneficio("a")) }
        val vm = BilleteraViewModel(repo, reloj); runCurrent()
        vm.onMostrarCodigo((vm.uiState.value as BilleteraUiState.Success).beneficios.first())
        repo.beneficios.clear()
        vm.refrescar(); runCurrent()
        assertNull((vm.uiState.value as BilleteraUiState.Success).mostrando)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BeneficioPromocionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun `obtener muestra el codigo y avisa que cambiaron los cupos`() = runTest {
        val repo = BilleteraFalsa()
        val vm = BeneficioPromocionViewModel("p1", repo); runCurrent()
        assertFalse(vm.uiState.value.cargando)
        vm.onObtener(); runCurrent()
        val estado = vm.uiState.value
        assertEquals("K7MP2QXR", estado.beneficio?.codigo)
        assertTrue(estado.mostrarCodigo); assertTrue(estado.recienObtenido)
        assertEquals(Unit, vm.obtenidos.first())
    }

    @Test fun `si ya lo tenia no vuelve a pedirlo`() = runTest {
        val repo = BilleteraFalsa().apply { existente = { BeneficioObtenido("b", "ABCDEFGH", promocionId = it) } }
        val vm = BeneficioPromocionViewModel("p1", repo); runCurrent()
        vm.onObtener(); runCurrent()
        assertEquals(0, repo.adquiridos)
        vm.onVerCodigo()
        assertTrue(vm.uiState.value.mostrarCodigo); assertFalse(vm.uiState.value.recienObtenido)
    }

    @Test fun `sin cupos muestra el motivo y actualiza el detalle`() = runTest {
        val repo = BilleteraFalsa().apply { adquirirCon = { throw BilleteraException(MotivoBilletera.SIN_CUPOS) } }
        val vm = BeneficioPromocionViewModel("p1", repo); runCurrent()
        vm.onObtener(); runCurrent()
        assertEquals(R.string.beneficio_error_sin_cupos, vm.uiState.value.error)
        assertNull(vm.uiState.value.beneficio)
        assertEquals(Unit, vm.obtenidos.first())
    }

    @Test fun `un fallo de red al revisar no bloquea el boton`() = runTest {
        val repo = BilleteraFalsa().apply { existente = { throw IOException() } }
        val vm = BeneficioPromocionViewModel("p1", repo); runCurrent()
        assertEquals(R.string.beneficio_error_estado, vm.uiState.value.error)
        assertFalse(vm.uiState.value.cargando)
        vm.onObtener(); runCurrent()
        assertNotNull(vm.uiState.value.beneficio)
    }

    @Test fun `un doble toque no obtiene dos veces`() = runTest {
        val repo = BilleteraFalsa()
        val vm = BeneficioPromocionViewModel("p1", repo); runCurrent()
        vm.onObtener(); vm.onObtener(); runCurrent()
        assertEquals(1, repo.adquiridos)
    }
}
