package com.cocinacapital.app.ui.perfil

import com.cocinacapital.app.R
import com.cocinacapital.app.data.auth.SesionDePrueba
import com.cocinacapital.app.data.local.ArchivoFoto
import com.cocinacapital.app.data.local.FavoritoEntity
import com.cocinacapital.app.data.local.FavoritosDaoEnMemoria
import com.cocinacapital.app.data.local.FotoPerfilArchivos
import com.cocinacapital.app.data.local.PreferenciasDaoEnMemoria
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import com.cocinacapital.app.data.model.TipoFavorito
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.PreferenciasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Preferencias, consentimiento, favoritos y datos de la cuenta en el Perfil (T-17). La foto está en PerfilViewModelFotoTest. */
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {
    private class Catalogo : HomeRepository {
        var falla = false
        override suspend fun getCategorias(): List<Categoria> {
            if (falla) error("sin red")
            return listOf(Categoria("c1", "Cocina Marina"), Categoria("c2", "Cafetería"))
        }
        override suspend fun getLocales() = emptyList<LocalGastronomico>()
        override suspend fun getPromocionesVigentes() = emptyList<Promocion>()
    }

    private class SinArchivos : FotoPerfilArchivos {
        override fun nuevoArchivoCamara() = ArchivoFoto("content://foto", "/fotos/foto.jpg")
        override suspend fun copiarDesde(uri: String) = "/fotos/copia.jpg"
        override fun eliminar(ruta: String) = Unit
    }

    private val sesion = SesionDePrueba()
    private val favoritosDao = FavoritosDaoEnMemoria()
    private val catalogo = Catalogo()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.modelo(): PerfilViewModel {
        val vm = PerfilViewModel(
            sesion,
            PreferenciasRepository(PreferenciasDaoEnMemoria(), sesion),
            FavoritosRepository(favoritosDao, sesion),
            catalogo,
            SinArchivos()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun PerfilViewModel.success() = uiState.value as PerfilUiState.Success

    @Test fun `muestra la cuenta, sus preferencias y las categorias`() = runTest {
        val vm = modelo()
        val state = vm.success()
        assertEquals("Ana Pérez", state.usuario?.nombre)
        assertEquals(listOf("c1", "c2"), state.categorias.map { it.id })
        assertFalse(state.preferencias.consentimiento)
    }

    @Test fun `si fallan las categorias muestra error y se puede reintentar`() = runTest {
        catalogo.falla = true
        val vm = modelo()
        assertEquals(PerfilUiState.Error(R.string.perfil_error_cargar), vm.uiState.value)
        catalogo.falla = false
        vm.onReintentar()
        assertIs<PerfilUiState.Success>(vm.uiState.value)
    }

    @Test fun `dar y revocar el consentimiento se refleja con su fecha`() = runTest {
        val vm = modelo()
        vm.onConsentimiento(true)
        assertTrue(vm.success().preferencias.consentimiento)
        assertNotNull(vm.success().preferencias.consentimientoFecha)
        vm.onConsentimiento(false)
        assertFalse(vm.success().preferencias.consentimiento)
        assertNull(vm.success().preferencias.consentimientoFecha)
    }

    @Test fun `cambia cocinas preferidas, precios, notificaciones y tema`() = runTest {
        val vm = modelo()
        vm.onCategoriaClick("c2")
        vm.onRangoPrecio(RangoPrecio.ECONOMICO)
        vm.onNotificaciones(true)
        vm.onSoloCategoriasFavoritas(true)
        vm.onTema(TemaApp.OSCURO)
        val prefs = vm.success().preferencias
        assertEquals(setOf("c2"), prefs.categoriasIds)
        assertEquals(RangoPrecio.ECONOMICO, prefs.rangoPrecio)
        assertTrue(prefs.notificaciones)
        assertTrue(prefs.soloCategoriasFavoritas)
        assertEquals(TemaApp.OSCURO, prefs.tema)
    }

    @Test fun `el nombre se guarda sin espacios y exige al menos 2 letras`() = runTest {
        val vm = modelo()
        vm.onGuardarNombre("  A ")
        assertEquals("Ana Pérez", vm.success().usuario?.nombre)
        vm.onGuardarNombre("  Ana María  ")
        assertEquals("Ana María", vm.success().usuario?.nombre)
    }

    @Test fun `quitar un favorito lo saca de la lista`() = runTest {
        favoritosDao.guardar(FavoritoEntity("ana", "l5", "LOCAL", "Sushi Muelle", "Sushi", null))
        val vm = modelo()
        val favorito = vm.success().favoritos.single()
        assertEquals(Favorito("l5", TipoFavorito.LOCAL, "Sushi Muelle", "Sushi"), favorito)
        vm.onQuitarFavorito(favorito)
        assertTrue(vm.success().favoritos.isEmpty())
    }

    @Test fun `cerrar sesion termina la sesion`() = runTest {
        val vm = modelo()
        vm.onCerrarSesion()
        assertTrue(sesion.cerroSesion)
        assertNull(vm.success().usuario)
    }
}
