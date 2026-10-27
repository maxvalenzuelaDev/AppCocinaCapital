package com.cocinacapital.app.ui.oferente

import com.cocinacapital.app.R
import com.cocinacapital.app.data.location.EstadoUbicacion
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.repository.DatosLocal
import com.cocinacapital.app.data.repository.MisLocalesRepository
import com.cocinacapital.location.LocationAccess
import com.cocinacapital.location.LocationProblem
import com.cocinacapital.location.UserPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MisLocalesViewModelTest {
    private val categorias = listOf(Categoria("cat-1", "Cafetería"), Categoria("cat-2", "Sushi"))

    private class FakeRepo(
        locales: List<LocalGastronomico> = emptyList(),
        var fallaCarga: Boolean = false,
        var fallaGuardado: Boolean = false,
        val categorias: List<Categoria>
    ) : MisLocalesRepository {
        val locales = locales.toMutableList()
        val guardados = mutableListOf<Pair<String?, DatosLocal>>()
        private var siguiente = 1
        override suspend fun listar(): List<LocalGastronomico> {
            if (fallaCarga) error("sin conexión")
            return locales.sortedBy { it.nombre }
        }
        override suspend fun getCategorias() = categorias
        override suspend fun guardar(localId: String?, datos: DatosLocal): LocalGastronomico {
            if (fallaGuardado) error("rechazado")
            guardados += localId to datos
            val guardado = LocalGastronomico(
                id = localId ?: "nuevo-${siguiente++}", nombre = datos.nombre, categoriaId = datos.categoriaId,
                comuna = datos.comuna, direccion = datos.direccion, descripcion = datos.descripcion,
                horario = datos.horario, ticketPromedioPersona = datos.ticketPromedioPersona,
                latitud = datos.latitud, longitud = datos.longitud
            )
            locales.removeAll { it.id == guardado.id }
            locales += guardado
            return guardado
        }
    }

    private class FakeUbicacion : UbicacionRepository {
        override val estado = MutableStateFlow(EstadoUbicacion())
        override val posicion = flowOf(null)
        override suspend fun obtener() = Unit
        override fun limpiar() = Unit
        override fun permisoDenegado() = Unit
        override fun validarDisponibilidad() = Unit
    }

    private val ubicacion = FakeUbicacion()

    private fun fix(ts: Long, lat: Double = -33.04, lon: Double = -71.62) =
        UserPosition(lat, lon, 18f, ts, LocationAccess.PRECISE, fromCache = false)

    private val cafe = LocalGastronomico(
        id = "local-1", nombre = "Café Cerro Alegre", categoriaId = "cat-1", comuna = "Valparaíso",
        direccion = "Calle Templeman 345", horario = "Lun a Sáb 09:00–20:00", ticketPromedioPersona = 6500
    )
    private val sushi = LocalGastronomico(id = "local-2", nombre = "Sushi Muelle", categoriaId = "cat-2", comuna = "Valparaíso")

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun modelo(repo: FakeRepo) = MisLocalesViewModel(repo, ubicacion)
    private fun MisLocalesViewModel.success() = uiState.value as MisLocalesUiState.Success
    private fun MisLocalesViewModel.editor() = success().editor!!

    @Test fun `sin locales muestra la lista vacia sin local elegido`() {
        val model = modelo(FakeRepo(categorias = categorias))
        assertTrue(model.success().locales.isEmpty())
        assertNull(model.success().seleccionadoId)
        assertNull(model.success().editor)
        assertEquals(categorias, model.success().categorias)
    }

    @Test fun `lista todos los locales y elige el primero`() {
        val model = modelo(FakeRepo(listOf(sushi, cafe), categorias = categorias))
        assertEquals(listOf("Café Cerro Alegre", "Sushi Muelle"), model.success().locales.map { it.nombre })
        assertEquals("local-1", model.success().seleccionadoId)
    }

    @Test fun `se puede elegir otro local pero no uno ajeno`() {
        val model = modelo(FakeRepo(listOf(cafe, sushi), categorias = categorias))
        model.onSeleccionar("local-2")
        assertEquals(sushi, model.success().seleccionado)
        model.onSeleccionar("otro")
        assertEquals("local-2", model.success().seleccionadoId)
    }

    @Test fun `si falla la carga muestra error y se puede reintentar`() {
        val repo = FakeRepo(fallaCarga = true, categorias = categorias)
        val model = modelo(repo)
        assertEquals(MisLocalesUiState.Error(R.string.mi_local_error_cargar), model.uiState.value)
        repo.fallaCarga = false
        model.onReintentar()
        assertIs<MisLocalesUiState.Success>(model.uiState.value)
    }

    @Test fun `editar abre el formulario con los datos del local`() {
        val model = modelo(FakeRepo(listOf(cafe), categorias = categorias))
        model.onEditar(cafe)
        assertEquals("local-1", model.editor().localId)
        assertEquals("Café Cerro Alegre", model.editor().formulario.nombre)
        assertEquals("6500", model.editor().formulario.ticket)
        model.onCerrarEditor()
        assertNull(model.success().editor)
    }

    @Test fun `no guarda si faltan datos obligatorios`() {
        val repo = FakeRepo(categorias = categorias)
        val model = modelo(repo)
        model.onNuevo()
        model.onGuardar()
        val errores = model.editor().errores
        assertEquals(R.string.mi_local_error_obligatorio, errores.nombre)
        assertEquals(R.string.mi_local_error_categoria, errores.categoria)
        assertEquals(R.string.mi_local_error_obligatorio, errores.comuna)
        assertEquals(R.string.mi_local_error_obligatorio, errores.direccion)
        assertTrue(repo.guardados.isEmpty())
    }

    @Test fun `crea otro local, vuelve a la lista y lo deja elegido`() = runTest {
        val repo = FakeRepo(listOf(cafe), categorias = categorias)
        val model = modelo(repo)
        model.onNuevo()
        model.onCambio {
            copy(nombre = "  Sushi Norte ", categoriaId = "cat-2", comuna = "Viña del Mar",
                direccion = "Av. Libertad 100", ticket = "$15.000")
        }
        model.onGuardar()
        val (localId, datos) = repo.guardados.single()
        assertNull(localId)
        assertEquals("Sushi Norte", datos.nombre)
        assertNull(datos.horario)
        assertEquals(15000L, datos.ticketPromedioPersona)
        assertNull(model.success().editor)
        assertEquals(listOf("Café Cerro Alegre", "Sushi Norte"), model.success().locales.map { it.nombre })
        assertEquals("nuevo-1", model.success().seleccionadoId)
        assertEquals(R.string.mi_local_creado, model.mensajes.first())
    }

    @Test fun `edita un local sin cambiar el elegido`() = runTest {
        val repo = FakeRepo(listOf(cafe, sushi), categorias = categorias)
        val model = modelo(repo)
        model.onEditar(sushi)
        model.onCambio { copy(direccion = "Calle Blanco 980") }
        model.onGuardar()
        assertEquals("local-2", repo.guardados.single().first)
        assertEquals("Calle Blanco 980", model.success().locales.first { it.id == "local-2" }.direccion)
        assertEquals("local-1", model.success().seleccionadoId)
        assertEquals(R.string.mi_local_guardado, model.mensajes.first())
    }

    @Test fun `si falla el guardado conserva lo escrito y avisa`() = runTest {
        val repo = FakeRepo(listOf(cafe), fallaGuardado = true, categorias = categorias)
        val model = modelo(repo)
        model.onEditar(cafe)
        model.onCambio { copy(nombre = "Nuevo nombre") }
        model.onGuardar()
        assertEquals("Nuevo nombre", model.editor().formulario.nombre)
        assertFalse(model.editor().guardando)
        assertEquals(R.string.mi_local_error_guardar, model.mensajes.first())
    }

    @Test fun `el consumo por persona acepta el formato chileno`() {
        assertNull(ticketDesde(""))
        assertEquals(12000L, ticketDesde("12000"))
        assertEquals(12000L, ticketDesde("12.000"))
        assertEquals(12000L, ticketDesde(" $12.000 "))
        assertEquals(R.string.mi_local_error_ticket, validarLocal(FormularioLocal(ticket = "doce mil"), categorias).second.ticket)
        assertEquals(R.string.mi_local_error_ticket, validarLocal(FormularioLocal(ticket = "0"), categorias).second.ticket)
        assertEquals(R.string.mi_local_error_ticket, validarLocal(FormularioLocal(ticket = "12,5"), categorias).second.ticket)
    }

    @Test fun `rechaza textos demasiado largos y categorias que no existen`() {
        val errores = validarLocal(
            FormularioLocal(nombre = "a".repeat(MAX_NOMBRE + 1), categoriaId = "otra", comuna = "x", direccion = "y"),
            categorias
        ).second
        assertEquals(R.string.mi_local_error_largo, errores.nombre)
        assertEquals(R.string.mi_local_error_categoria, errores.categoria)
    }

    @Test fun `al pulsar el boton usa la nueva posicion y la guarda con el local`() {
        val repo = FakeRepo(listOf(cafe), categorias = categorias)
        val model = modelo(repo)
        model.onEditar(cafe)
        model.onUsarUbicacion()
        ubicacion.estado.value = EstadoUbicacion(cargando = true)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 10, lat = -33.0425, lon = -71.6285))
        assertEquals(-33.0425, model.editor().formulario.latitud)
        assertEquals(-71.6285, model.editor().formulario.longitud)
        assertEquals(18f, model.editor().precisionMetros)
        model.onGuardar()
        assertEquals(-33.0425, repo.guardados.single().second.latitud)
    }

    @Test fun `no toma la ubicacion si el oferente no pulso el boton`() {
        val model = modelo(FakeRepo(categorias = categorias))
        model.onNuevo()
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 10))
        assertNull(model.editor().formulario.latitud)
    }

    @Test fun `acepta la misma posicion en cache si la consulta pedida ya empezo`() {
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 5))
        val model = modelo(FakeRepo(categorias = categorias))
        model.onNuevo()
        model.onUsarUbicacion()
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 5), cargando = true)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 5))
        assertEquals(-33.04, model.editor().formulario.latitud)
    }

    @Test fun `un error de ubicacion cancela la espera`() {
        val model = modelo(FakeRepo(categorias = categorias))
        model.onNuevo()
        model.onUsarUbicacion()
        ubicacion.estado.value = EstadoUbicacion(problema = LocationProblem.PERMISSION_REQUIRED)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 10))
        assertNull(model.editor().formulario.latitud)
    }
}
