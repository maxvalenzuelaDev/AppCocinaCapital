package com.cocinacapital.app.ui.detalle

import com.cocinacapital.app.data.auth.FakePerfilSesion
import com.cocinacapital.app.data.local.FavoritoEntity
import com.cocinacapital.app.data.local.FavoritosDao
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.DetalleLocalRepository
import com.cocinacapital.app.data.repository.FavoritosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleLocalViewModelTest {
    private class FakeRepo : DetalleLocalRepository {
        var local: LocalGastronomico? = LocalGastronomico("l1", "Caleta del Puerto", "c1", "Valparaíso", categoriaNombre = "Cocina Marina")
        var failure: Exception? = null
        val platos = listOf(Plato("pl1", "l1", "Pastel de jaiba", precio = 12900, destacado = true))
        val promociones = listOf(Promocion("p1", "l1", "2x1", descuentoPorcentaje = 50, vigenteHasta = "2030-01-01T00:00:00Z"))
        override suspend fun getLocal(id: String) = failure?.let { throw it } ?: local?.takeIf { it.id == id }
        override suspend fun getPlatosDestacados(localId: String) = platos.filter { it.localId == localId }
        override suspend fun getPromocionesVigentes(localId: String) = promociones.filter { it.localId == localId }
    }

    private class FakeFavoritosDao : FavoritosDao {
        val filas = MutableStateFlow<List<FavoritoEntity>>(emptyList())
        override fun observar(userId: String): Flow<List<FavoritoEntity>> = filas.map { l -> l.filter { it.userId == userId } }
        override suspend fun existe(userId: String, itemId: String, tipo: String) =
            filas.value.count { it.userId == userId && it.itemId == itemId && it.tipo == tipo }
        override suspend fun guardar(favorito: FavoritoEntity) { filas.value = filas.value + favorito }
        override suspend fun eliminar(userId: String, itemId: String, tipo: String) {
            filas.value = filas.value.filterNot { it.userId == userId && it.itemId == itemId && it.tipo == tipo }
        }
    }

    private val favoritosDao = FakeFavoritosDao()
    private val favoritos = FavoritosRepository(favoritosDao, FakePerfilSesion())

    private fun viewModel(localId: String = "l1", repo: FakeRepo = FakeRepo()) = DetalleLocalViewModel(localId, repo, favoritos)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun `carga local, menu y promociones con el nombre del local`() {
        val state = assertIs<DetalleLocalUiState.Success>(viewModel().uiState.value)
        assertEquals("Caleta del Puerto", state.local.nombre)
        assertEquals(listOf("Pastel de jaiba"), state.platosDestacados.map { it.nombre })
        assertEquals("Caleta del Puerto", state.promociones.single().localNombre)
        assertEquals("c1", state.promociones.single().localCategoriaId)
    }
    @Test fun `local inexistente muestra no encontrado`() {
        assertEquals(DetalleLocalUiState.NoEncontrado, viewModel("otro").uiState.value)
    }
    @Test fun `error de red muestra error`() {
        val repo = FakeRepo().apply { failure = IllegalStateException("sin red") }
        assertEquals(DetalleLocalUiState.Error, viewModel(repo = repo).uiState.value)
    }
    @Test fun `reintentar recupera el detalle`() {
        val repo = FakeRepo().apply { failure = IllegalStateException("sin red") }
        val viewModel = viewModel(repo = repo)
        repo.failure = null
        viewModel.onReintentar()
        assertIs<DetalleLocalUiState.Success>(viewModel.uiState.value)
    }
    @Test fun `el corazon agrega y quita el local de favoritos`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.esFavorito.collect {} }
        assertFalse(viewModel.esFavorito.value)
        viewModel.onFavoritoClick()
        assertTrue(viewModel.esFavorito.value)
        assertEquals("Cocina Marina · Valparaíso", favoritosDao.filas.value.single().subtitulo)
        viewModel.onFavoritoClick()
        assertFalse(viewModel.esFavorito.value)
    }
    @Test fun `sin detalle cargado el corazon no hace nada`() {
        val viewModel = viewModel(repo = FakeRepo().apply { failure = IllegalStateException("sin red") })
        viewModel.onFavoritoClick()
        assertTrue(favoritosDao.filas.value.isEmpty())
    }
}
