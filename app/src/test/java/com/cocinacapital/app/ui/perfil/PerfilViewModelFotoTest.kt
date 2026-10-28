package com.cocinacapital.app.ui.perfil

import com.cocinacapital.app.data.auth.FakePerfilSesion
import com.cocinacapital.app.data.local.ArchivoFoto
import com.cocinacapital.app.data.local.CategoriaPreferidaEntity
import com.cocinacapital.app.data.local.FavoritoEntity
import com.cocinacapital.app.data.local.FavoritosDao
import com.cocinacapital.app.data.local.FotoPerfilArchivos
import com.cocinacapital.app.data.local.PreferenciasDao
import com.cocinacapital.app.data.local.PreferenciasEntity
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.PreferenciasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelFotoTest {
    private class FakePreferenciasDao : PreferenciasDao {
        val filas = MutableStateFlow<Map<String, PreferenciasEntity>>(emptyMap())
        override fun observar(userId: String): Flow<PreferenciasEntity?> = filas.map { it[userId] }
        override suspend fun obtener(userId: String) = filas.value[userId]
        override suspend fun guardar(preferencias: PreferenciasEntity) {
            filas.value = filas.value + (preferencias.userId to preferencias)
        }
        override fun observarCategorias(userId: String): Flow<List<String>> = flowOf(emptyList())
        override suspend fun obtenerCategorias(userId: String) = emptyList<String>()
        override suspend fun agregarCategoria(categoria: CategoriaPreferidaEntity) = Unit
        override suspend fun quitarCategoria(userId: String, categoriaId: String) = Unit
        override suspend fun borrarCategorias(userId: String) = Unit
        override suspend fun borrar(userId: String) = Unit
    }

    private class FakeFavoritosDao : FavoritosDao {
        override fun observar(userId: String): Flow<List<FavoritoEntity>> = flowOf(emptyList())
        override suspend fun existe(userId: String, itemId: String, tipo: String) = 0
        override suspend fun guardar(favorito: FavoritoEntity) = Unit
        override suspend fun eliminar(userId: String, itemId: String, tipo: String) = Unit
        override suspend fun borrarTodos(userId: String) = Unit
    }

    private class FakeCatalogo : HomeRepository {
        override suspend fun getCategorias() = emptyList<Categoria>()
        override suspend fun getLocales() = emptyList<LocalGastronomico>()
        override suspend fun getPromocionesVigentes() = emptyList<Promocion>()
    }

    private class FakeArchivos : FotoPerfilArchivos {
        private var contador = 0
        val eliminados = mutableListOf<String>()
        var falloCopia: Exception? = null
        override fun nuevoArchivoCamara() = (++contador).let { ArchivoFoto("content://foto$it", "/fotos/foto$it.jpg") }
        override suspend fun copiarDesde(uri: String): String {
            falloCopia?.let { throw it }
            return "/fotos/galeria${++contador}.jpg"
        }
        override fun eliminar(ruta: String) { eliminados += ruta }
    }

    private val dao = FakePreferenciasDao()
    private val archivos = FakeArchivos()
    private lateinit var viewModel: PerfilViewModel

    /** Ruta guardada en Room para el usuario de prueba. */
    private val rutaGuardada get() = dao.filas.value["usuario-demo"]?.fotoPerfilRuta

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val sesion = FakePerfilSesion()
        viewModel = PerfilViewModel(
            sesion,
            PreferenciasRepository(dao, sesion),
            FavoritosRepository(FakeFavoritosDao(), sesion),
            FakeCatalogo(),
            archivos
        )
    }
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun `con permiso abre la camara con un archivo nuevo`() {
        viewModel.onCambiarFoto()
        assertEquals(DialogoFoto.ELEGIR_ORIGEN, viewModel.foto.value.dialogo)
        viewModel.onElegirCamara(permisoConcedido = true)
        assertEquals(FotoPerfilUiState(accion = AccionFoto.AbrirCamara("content://foto1")), viewModel.foto.value)
    }
    @Test fun `sin permiso explica, pide el permiso y ofrece ajustes si se niega`() {
        viewModel.onElegirCamara(permisoConcedido = false)
        assertEquals(DialogoFoto.EXPLICAR_PERMISO, viewModel.foto.value.dialogo)
        viewModel.onAceptarExplicacionPermiso()
        assertEquals(AccionFoto.PedirPermisoCamara, viewModel.foto.value.accion)
        viewModel.onAccionFotoRealizada()
        viewModel.onResultadoPermisoCamara(concedido = false)
        assertEquals(FotoPerfilUiState(dialogo = DialogoFoto.PERMISO_DENEGADO), viewModel.foto.value)
        viewModel.onAbrirAjustes()
        assertEquals(FotoPerfilUiState(accion = AccionFoto.AbrirAjustes), viewModel.foto.value)
    }
    @Test fun `permiso concedido despues de pedirlo abre la camara`() {
        viewModel.onResultadoPermisoCamara(concedido = true)
        assertIs<AccionFoto.AbrirCamara>(viewModel.foto.value.accion)
    }
    @Test fun `foto tomada se guarda y reemplaza a la anterior`() {
        viewModel.onElegirCamara(true); viewModel.onFotoTomada(exito = true)
        assertEquals("/fotos/foto1.jpg", rutaGuardada)
        viewModel.onElegirCamara(true); viewModel.onFotoTomada(exito = true)
        assertEquals("/fotos/foto2.jpg", rutaGuardada)
        assertEquals(listOf("/fotos/foto1.jpg"), archivos.eliminados)
    }
    @Test fun `camara cancelada borra el archivo y no cambia la foto`() {
        viewModel.onElegirCamara(true)
        viewModel.onFotoTomada(exito = false)
        assertNull(rutaGuardada)
        assertEquals(listOf("/fotos/foto1.jpg"), archivos.eliminados)
    }
    @Test fun `foto de la galeria se copia y se guarda`() {
        viewModel.onElegirGaleria()
        assertEquals(AccionFoto.AbrirGaleria, viewModel.foto.value.accion)
        viewModel.onFotoElegida("content://galeria/1")
        assertEquals("/fotos/galeria1.jpg", rutaGuardada)
    }
    @Test fun `cerrar el selector sin elegir no cambia nada`() {
        viewModel.onFotoElegida(null)
        assertNull(rutaGuardada)
        assertEquals(FotoPerfilUiState(), viewModel.foto.value)
    }
    @Test fun `error al copiar muestra mensaje y conserva la foto`() {
        archivos.falloCopia = IOException("sin acceso")
        viewModel.onFotoElegida("content://galeria/1")
        assertEquals(DialogoFoto.ERROR_GUARDAR, viewModel.foto.value.dialogo)
        assertNull(rutaGuardada)
    }
    @Test fun `quitar foto borra la ruta y el archivo`() {
        viewModel.onFotoElegida("content://galeria/1")
        viewModel.onQuitarFoto()
        assertNull(rutaGuardada)
        assertEquals(listOf("/fotos/galeria1.jpg"), archivos.eliminados)
    }
}
