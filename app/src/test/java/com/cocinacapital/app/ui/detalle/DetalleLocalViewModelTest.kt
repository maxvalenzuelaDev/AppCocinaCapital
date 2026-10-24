package com.cocinacapital.app.ui.detalle

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.DetalleLocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun `carga local, menu y promociones con el nombre del local`() {
        val state = assertIs<DetalleLocalUiState.Success>(DetalleLocalViewModel("l1", FakeRepo()).uiState.value)
        assertEquals("Caleta del Puerto", state.local.nombre)
        assertEquals(listOf("Pastel de jaiba"), state.platosDestacados.map { it.nombre })
        assertEquals("Caleta del Puerto", state.promociones.single().localNombre)
        assertEquals("c1", state.promociones.single().localCategoriaId)
    }
    @Test fun `local inexistente muestra no encontrado`() {
        assertEquals(DetalleLocalUiState.NoEncontrado, DetalleLocalViewModel("otro", FakeRepo()).uiState.value)
    }
    @Test fun `error de red muestra error`() {
        val repo = FakeRepo().apply { failure = IllegalStateException("sin red") }
        assertEquals(DetalleLocalUiState.Error, DetalleLocalViewModel("l1", repo).uiState.value)
    }
    @Test fun `reintentar recupera el detalle`() {
        val repo = FakeRepo().apply { failure = IllegalStateException("sin red") }
        val viewModel = DetalleLocalViewModel("l1", repo)
        repo.failure = null
        viewModel.onReintentar()
        assertIs<DetalleLocalUiState.Success>(viewModel.uiState.value)
    }
}
