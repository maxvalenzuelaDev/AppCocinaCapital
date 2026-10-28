package com.cocinacapital.app.ui.oferente

import com.cocinacapital.app.R
import com.cocinacapital.app.data.local.ArchivoFoto
import com.cocinacapital.app.data.local.LectorQr
import com.cocinacapital.app.data.model.EstadoCanje
import com.cocinacapital.app.data.model.ResultadoCanje
import com.cocinacapital.app.data.repository.BilleteraException
import com.cocinacapital.app.data.repository.CanjeRepository
import com.cocinacapital.app.data.repository.MotivoBilletera
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CanjeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }

    private fun resultado(estado: EstadoCanje, ahora: Boolean = false) = ResultadoCanje(
        codigo = "ABCDEFGH", estado = estado, titulo = "2x1 en pastel de jaiba", descuentoPorcentaje = 50,
        vigenteHasta = "2026-11-01T03:00:00Z", localNombre = "Caleta del Puerto", canjeadoAhora = ahora,
        canjeadoEn = if (estado == EstadoCanje.CANJEADO) "2026-10-28T15:00:00Z" else null
    )

    private inner class Canje : CanjeRepository {
        var consultarCon: suspend (String) -> ResultadoCanje = { resultado(EstadoCanje.VALIDO) }
        var canjearCon: suspend (String) -> ResultadoCanje = { resultado(EstadoCanje.CANJEADO, ahora = true) }
        val consultados = mutableListOf<String>()
        var canjes = 0
        override suspend fun consultar(codigo: String): ResultadoCanje { consultados += codigo; return consultarCon(codigo) }
        override suspend fun canjear(codigo: String): ResultadoCanje { canjes++; return canjearCon(codigo) }
    }

    private class Lector : LectorQr {
        var texto: String? = null
        val descartadas = mutableListOf<String>()
        override fun nuevaCaptura() = ArchivoFoto("content://qr/1", "/cache/qr_1.jpg")
        override suspend fun leer(ruta: String) = texto
        override fun descartar(ruta: String) { descartadas += ruta }
    }

    @Test fun `revisa el codigo escrito y luego confirma el canje`() = runTest {
        val repo = Canje()
        val vm = CanjeViewModel(repo, Lector())
        vm.onCodigo("abcd-efgh"); vm.onRevisar(); runCurrent()
        assertEquals(listOf("ABCDEFGH"), repo.consultados)
        assertEquals(EstadoCanje.VALIDO, vm.uiState.value.resultado?.estado)
        assertEquals(0, repo.canjes)
        vm.onConfirmar(); runCurrent()
        assertTrue(vm.uiState.value.resultado!!.canjeadoAhora)
        assertEquals(1, repo.canjes)
    }

    @Test fun `un codigo mal escrito no consulta Supabase`() = runTest {
        val repo = Canje()
        val vm = CanjeViewModel(repo, Lector())
        vm.onCodigo("ABC12"); vm.onRevisar(); runCurrent()
        assertEquals(R.string.canje_codigo_invalido, vm.uiState.value.error)
        assertTrue(repo.consultados.isEmpty())
    }

    @Test fun `el campo limita el largo y pasa a mayusculas`() {
        val vm = CanjeViewModel(Canje(), Lector())
        vm.onCodigo("abcd-efgh-xyz!")
        assertEquals("ABCD-EFGH", vm.uiState.value.codigo)
    }

    @Test fun `un codigo ya canjeado vencido o fuera de horario se informa tal cual`() = runTest {
        for (estado in listOf(EstadoCanje.CANJEADO, EstadoCanje.VENCIDO, EstadoCanje.FUERA_DE_HORARIO)) {
            val vm = CanjeViewModel(Canje().apply { consultarCon = { resultado(estado) } }, Lector())
            vm.onCodigo("ABCDEFGH"); vm.onRevisar(); runCurrent()
            assertEquals(estado, vm.uiState.value.resultado?.estado)
            assertFalse(vm.uiState.value.resultado!!.canjeadoAhora)
        }
    }

    @Test fun `un codigo de otro local se informa como no encontrado`() = runTest {
        val repo = Canje().apply { consultarCon = { throw BilleteraException(MotivoBilletera.CODIGO_NO_ENCONTRADO) } }
        val vm = CanjeViewModel(repo, Lector())
        vm.onCodigo("ABCDEFGH"); vm.onRevisar(); runCurrent()
        assertEquals(R.string.canje_error_no_encontrado, vm.uiState.value.error)
        assertNull(vm.uiState.value.resultado)
    }

    @Test fun `si falla la red al confirmar se conserva la revision para reintentar`() = runTest {
        val repo = Canje().apply { canjearCon = { throw IOException() } }
        val vm = CanjeViewModel(repo, Lector())
        vm.onCodigo("ABCDEFGH"); vm.onRevisar(); runCurrent()
        vm.onConfirmar(); runCurrent()
        assertEquals(R.string.canje_error_red, vm.uiState.value.error)
        assertEquals(EstadoCanje.VALIDO, vm.uiState.value.resultado?.estado)
    }

    @Test fun `el QR escaneado se revisa automaticamente`() = runTest {
        val repo = Canje()
        val vm = CanjeViewModel(repo, Lector().apply { texto = "cocinacapital:beneficio:ABCDEFGH" })
        assertEquals("content://qr/1", vm.onEscanear())
        vm.onFotoTomada(true); runCurrent()
        assertEquals("ABCD-EFGH", vm.uiState.value.codigo)
        assertEquals(listOf("ABCDEFGH"), repo.consultados)
    }

    @Test fun `un QR ajeno o ilegible se explica sin consultar`() = runTest {
        val repo = Canje()
        val lector = Lector().apply { texto = "https://ejemplo.cl" }
        val vm = CanjeViewModel(repo, lector)
        vm.onEscanear(); vm.onFotoTomada(true); runCurrent()
        assertEquals(R.string.canje_qr_ajeno, vm.uiState.value.error)
        lector.texto = null
        vm.onEscanear(); vm.onFotoTomada(true); runCurrent()
        assertEquals(R.string.canje_qr_no_leido, vm.uiState.value.error)
        assertTrue(repo.consultados.isEmpty())
    }

    @Test fun `si se cancela la camara se borra la foto`() {
        val lector = Lector()
        val vm = CanjeViewModel(Canje(), lector)
        vm.onEscanear(); vm.onFotoTomada(false)
        assertEquals(listOf("/cache/qr_1.jpg"), lector.descartadas)
        assertFalse(vm.uiState.value.ocupado)
    }
}
