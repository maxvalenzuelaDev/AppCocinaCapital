package com.cocinacapital.app.ui.oferente

import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.repository.CatalogoRepository
import com.cocinacapital.app.data.repository.DatosLocal
import com.cocinacapital.app.data.repository.DatosPlato
import com.cocinacapital.app.data.repository.MiLocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogoViewModelTest {
    private val local = LocalGastronomico(id = "local-1", nombre = "Sushi Muelle", categoriaId = "cat-1", comuna = "Valparaíso")

    private class FakeMiLocal(var local: LocalGastronomico?) : MiLocalRepository {
        override suspend fun obtenerMiLocal() = local
        override suspend fun getCategorias() = emptyList<Categoria>()
        override suspend fun guardar(localId: String?, datos: DatosLocal) = error("no se usa")
    }

    private class FakeCatalogo(var falla: Boolean = false) : CatalogoRepository {
        val platos = mutableListOf(
            Plato("p-2", "local-1", "Tabla Muelle", "20 piezas", 15900, destacado = true),
            Plato("p-1", "local-1", "Handroll de salmón", "", 4500)
        )
        private var siguiente = 10
        override suspend fun listar(localId: String) = platos.filter { it.localId == localId }.sortedBy { it.nombre }
        override suspend fun crear(localId: String, datos: DatosPlato): Plato {
            if (falla) error("rechazado")
            return Plato("p-${siguiente++}", localId, datos.nombre, datos.descripcion, datos.precio, datos.destacado).also { platos += it }
        }
        override suspend fun actualizar(platoId: String, datos: DatosPlato): Plato {
            if (falla) error("rechazado")
            val i = platos.indexOfFirst { it.id == platoId }
            return platos[i].copy(nombre = datos.nombre, descripcion = datos.descripcion, precio = datos.precio, destacado = datos.destacado)
                .also { platos[i] = it }
        }
        override suspend fun eliminar(platoId: String) {
            if (falla) error("rechazado")
            platos.removeAll { it.id == platoId }
        }
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun CatalogoViewModel.success() = uiState.value as CatalogoUiState.Success

    @Test fun `sin local pide registrarlo y al volver a la pestana lo carga`() {
        val miLocal = FakeMiLocal(null)
        val model = CatalogoViewModel(miLocal, FakeCatalogo())
        assertEquals(CatalogoUiState.SinLocal, model.uiState.value)
        miLocal.local = local
        model.onMostrar()
        assertEquals(2, model.success().platos.size)
    }

    @Test fun `lista los platos del local`() {
        val model = CatalogoViewModel(FakeMiLocal(local), FakeCatalogo())
        assertEquals(listOf("Handroll de salmón", "Tabla Muelle"), model.success().platos.map { it.nombre })
    }

    @Test fun `agrega un plato con precio en formato chileno`() = runTest {
        val catalogo = FakeCatalogo()
        val model = CatalogoViewModel(FakeMiLocal(local), catalogo)
        model.onAgregar()
        model.onCambio { copy(nombre = " Gyozas ", precio = "$5.900", destacado = true) }
        model.onGuardar()
        val nuevo = catalogo.platos.last()
        assertEquals("Gyozas", nuevo.nombre)
        assertEquals(5900, nuevo.precio)
        assertTrue(nuevo.destacado)
        assertNull(model.success().editor)
        assertEquals(listOf("Gyozas", "Handroll de salmón", "Tabla Muelle"), model.success().platos.map { it.nombre })
        assertEquals(R.string.catalogo_creado, model.mensajes.first())
    }

    @Test fun `no guarda un plato sin nombre ni precio`() {
        val catalogo = FakeCatalogo()
        val model = CatalogoViewModel(FakeMiLocal(local), catalogo)
        model.onAgregar()
        model.onGuardar()
        val editor = model.success().editor!!
        assertEquals(R.string.mi_local_error_obligatorio, editor.errorNombre)
        assertEquals(R.string.mi_local_error_obligatorio, editor.errorPrecio)
        assertEquals(2, catalogo.platos.size)
        model.onCambio { copy(nombre = "Gyozas", precio = "cinco mil") }
        model.onGuardar()
        assertEquals(R.string.catalogo_error_precio, model.success().editor!!.errorPrecio)
    }

    @Test fun `edita un plato existente`() = runTest {
        val catalogo = FakeCatalogo()
        val model = CatalogoViewModel(FakeMiLocal(local), catalogo)
        model.onEditar(model.success().platos.first { it.id == "p-1" })
        assertEquals("4500", model.success().editor!!.precio)
        model.onCambio { copy(precio = "4.900", destacado = true) }
        model.onGuardar()
        val editado = model.success().platos.first { it.id == "p-1" }
        assertEquals(4900, editado.precio)
        assertTrue(editado.destacado)
        assertEquals(R.string.catalogo_guardado, model.mensajes.first())
    }

    @Test fun `elimina un plato despues de confirmar`() = runTest {
        val catalogo = FakeCatalogo()
        val model = CatalogoViewModel(FakeMiLocal(local), catalogo)
        val plato = model.success().platos.first()
        model.onPedirEliminar(plato)
        assertEquals(plato, model.success().porEliminar)
        model.onConfirmarEliminar()
        assertNull(model.success().porEliminar)
        assertFalse(model.success().platos.any { it.id == plato.id })
        assertEquals(R.string.catalogo_eliminado, model.mensajes.first())
    }

    @Test fun `si falla el guardado el dialogo sigue abierto con lo escrito`() = runTest {
        val model = CatalogoViewModel(FakeMiLocal(local), FakeCatalogo(falla = true))
        model.onAgregar()
        model.onCambio { copy(nombre = "Gyozas", precio = "5900") }
        model.onGuardar()
        assertEquals("Gyozas", model.success().editor!!.nombre)
        assertFalse(model.success().guardando)
        assertEquals(R.string.catalogo_error_guardar, model.mensajes.first())
    }

    @Test fun `si falla el borrado el plato sigue en la lista`() = runTest {
        val model = CatalogoViewModel(FakeMiLocal(local), FakeCatalogo(falla = true))
        model.onPedirEliminar(model.success().platos.first())
        model.onConfirmarEliminar()
        assertEquals(2, model.success().platos.size)
        assertEquals(R.string.catalogo_error_eliminar, model.mensajes.first())
    }
}
