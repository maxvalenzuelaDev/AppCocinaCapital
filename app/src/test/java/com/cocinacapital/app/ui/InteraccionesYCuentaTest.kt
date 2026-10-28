package com.cocinacapital.app.ui

import androidx.lifecycle.SavedStateHandle
import com.cocinacapital.app.R
import com.cocinacapital.app.data.auth.SesionDePrueba
import com.cocinacapital.app.data.local.ArchivoFoto
import com.cocinacapital.app.data.local.FavoritosDaoEnMemoria
import com.cocinacapital.app.data.local.FotoPerfilArchivos
import com.cocinacapital.app.data.local.PreferenciasDaoEnMemoria
import com.cocinacapital.app.data.location.AndroidUbicacionRepository
import com.cocinacapital.app.data.location.FuenteUbicacionPrueba
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.PreferenciasUsuario
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.DetallePromocionRepository
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.GestionCuenta
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.Interaccion
import com.cocinacapital.app.data.repository.PreferenciasRepository
import com.cocinacapital.app.data.repository.PromocionDetalle
import com.cocinacapital.app.data.repository.RegistroInteracciones
import com.cocinacapital.app.data.repository.TipoInteraccion
import com.cocinacapital.app.ui.detalle.DetallePromocionViewModel
import com.cocinacapital.app.ui.explore.ExplorarViewModel
import com.cocinacapital.app.ui.perfil.PerfilViewModel
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

/** HU-20 (registro de interacciones) y T-07 (borrar historial y eliminar cuenta). */
@OptIn(ExperimentalCoroutinesApi::class)
class InteraccionesYCuentaTest {
    private class Registro : RegistroInteracciones {
        val registradas = mutableListOf<Interaccion>()
        override fun registrar(interaccion: Interaccion) { registradas += interaccion }
    }

    private class Catalogo : HomeRepository {
        override suspend fun getCategorias() = listOf(Categoria("c1", "Sushi"))
        override suspend fun getLocales() = emptyList<LocalGastronomico>()
        override suspend fun getPromocionesVigentes() = emptyList<Promocion>()
    }

    private val registro = Registro()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun explorar() = ExplorarViewModel(
        Catalogo(), AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L },
        flowOf(PreferenciasUsuario()), SavedStateHandle(), registro
    )

    @Test fun `una busqueda se registra cuando el texto deja de cambiar`() = runTest {
        val vm = explorar()
        vm.texto("s"); vm.texto("su"); vm.texto("sus"); vm.texto("sushi")
        advanceTimeBy(1_000); runCurrent()
        assertTrue(registro.registradas.isEmpty())
        advanceTimeBy(600); runCurrent()
        assertEquals(listOf(Interaccion(TipoInteraccion.BUSQUEDA, termino = "sushi")), registro.registradas)
    }

    @Test fun `no se registran textos cortos ni la misma busqueda dos veces`() = runTest {
        val vm = explorar()
        vm.texto("pi"); advanceUntilIdle()
        vm.texto("pizza"); advanceUntilIdle()
        vm.texto(" Pizza "); advanceUntilIdle()
        assertEquals(listOf("pizza"), registro.registradas.map { it.termino })
    }

    @Test fun `abrir una promocion registra una sola vista aunque se actualicen los cupos`() = runTest {
        val promo = Promocion("p", "l", "Oferta", descuentoPorcentaje = 25,
            vigenteDesde = "2026-10-27T17:00:00Z", vigenteHasta = "2026-10-27T20:00:00Z")
        val repo = object : DetallePromocionRepository {
            override suspend fun getPromocion(id: String) = PromocionDetalle(promo, cuposConfigurados = false, horarioConfigurado = true)
            override suspend fun getLocal(localId: String): LocalGastronomico? = null
        }
        val vm = DetallePromocionViewModel("p", repo, Clock.fixed(Instant.parse("2026-10-27T18:00:00Z"), ZoneOffset.UTC), registro)
        runCurrent()
        vm.actualizarCupos(); runCurrent()
        vm.onReintentar(); runCurrent()
        assertEquals(listOf(Interaccion(TipoInteraccion.VISTA_PROMOCION, localId = "l", promocionId = "p")), registro.registradas)
    }

    // ---- T-07 ----

    private class Cuenta : GestionCuenta {
        var borrar: suspend () -> Int = { 3 }
        var eliminar: suspend () -> Unit = {}
        var eliminaciones = 0
        override suspend fun borrarHistorial() = borrar()
        override suspend fun eliminarCuenta() { eliminaciones++; eliminar() }
    }

    private object SinArchivos : FotoPerfilArchivos {
        override fun nuevoArchivoCamara() = ArchivoFoto("content://foto", "/fotos/foto.jpg")
        override suspend fun copiarDesde(uri: String) = "/fotos/copia.jpg"
        override fun eliminar(ruta: String) = Unit
    }

    private fun perfil(cuenta: Cuenta): PerfilViewModel {
        val sesion = SesionDePrueba()
        return PerfilViewModel(
            sesion, PreferenciasRepository(PreferenciasDaoEnMemoria(), sesion),
            FavoritosRepository(FavoritosDaoEnMemoria(), sesion), Catalogo(), SinArchivos, cuenta = cuenta
        )
    }

    @Test fun `borrar el historial avisa si funciono o si fallo la red`() = runTest {
        val cuenta = Cuenta()
        val vm = perfil(cuenta)
        vm.onBorrarHistorial(); runCurrent()
        assertEquals(R.string.perfil_historial_borrado, vm.mensajes.first())
        cuenta.borrar = { throw IOException("sin red") }
        vm.onBorrarHistorial(); runCurrent()
        assertEquals(R.string.perfil_error_borrar_historial, vm.mensajes.first())
    }

    @Test fun `si falla eliminar la cuenta se avisa y se puede reintentar`() = runTest {
        val cuenta = Cuenta().apply { eliminar = { throw IOException("sin red") } }
        val vm = perfil(cuenta)
        vm.onEliminarCuenta(); runCurrent()
        assertEquals(R.string.perfil_error_eliminar_cuenta, vm.mensajes.first())
        assertFalse(vm.eliminandoCuenta.value)
        cuenta.eliminar = {}
        vm.onEliminarCuenta(); runCurrent()
        assertEquals(2, cuenta.eliminaciones)
    }

    @Test fun `un doble toque no pide eliminar la cuenta dos veces`() = runTest {
        val cuenta = Cuenta()
        val vm = perfil(cuenta)
        vm.onEliminarCuenta(); vm.onEliminarCuenta(); runCurrent()
        assertEquals(1, cuenta.eliminaciones)
    }
}
