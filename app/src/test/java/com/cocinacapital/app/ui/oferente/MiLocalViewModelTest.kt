package com.cocinacapital.app.ui.oferente

import com.cocinacapital.app.R
import com.cocinacapital.app.data.location.EstadoUbicacion
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.repository.DatosLocal
import com.cocinacapital.app.data.repository.MiLocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.cocinacapital.location.LocationAccess
import com.cocinacapital.location.LocationProblem
import com.cocinacapital.location.UserPosition
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MiLocalViewModelTest {
    private val categorias = listOf(Categoria("cat-1", "Cafetería"), Categoria("cat-2", "Sushi"))

    private class FakeRepo(
        var local: LocalGastronomico? = null,
        var fallaCarga: Boolean = false,
        var fallaGuardado: Boolean = false,
        val categorias: List<Categoria>
    ) : MiLocalRepository {
        val guardados = mutableListOf<Pair<String?, DatosLocal>>()
        override suspend fun obtenerMiLocal(): LocalGastronomico? {
            if (fallaCarga) error("sin conexión")
            return local
        }
        override suspend fun getCategorias() = categorias
        override suspend fun guardar(localId: String?, datos: DatosLocal): LocalGastronomico {
            if (fallaGuardado) error("rechazado")
            guardados += localId to datos
            return LocalGastronomico(
                id = localId ?: "nuevo-1", nombre = datos.nombre, categoriaId = datos.categoriaId,
                comuna = datos.comuna, direccion = datos.direccion, descripcion = datos.descripcion,
                horario = datos.horario, ticketPromedioPersona = datos.ticketPromedioPersona
            ).also { local = it }
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

    private val existente = LocalGastronomico(
        id = "local-1", nombre = "Café Cerro Alegre", categoriaId = "cat-1", comuna = "Valparaíso",
        direccion = "Calle Templeman 345", horario = "Lun a Sáb 09:00–20:00", ticketPromedioPersona = 6500
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun MiLocalViewModel.success() = uiState.value as MiLocalUiState.Success

    @Test fun `sin local muestra el formulario vacio para registrarlo`() {
        val model = MiLocalViewModel(FakeRepo(categorias = categorias), ubicacion)
        assertNull(model.success().localId)
        assertEquals(FormularioLocal(), model.success().formulario)
        assertEquals(categorias, model.success().categorias)
    }

    @Test fun `con local carga sus datos en el formulario`() {
        val model = MiLocalViewModel(FakeRepo(local = existente, categorias = categorias), ubicacion)
        assertEquals("local-1", model.success().localId)
        assertEquals("Café Cerro Alegre", model.success().formulario.nombre)
        assertEquals("6500", model.success().formulario.ticket)
    }

    @Test fun `si falla la carga muestra error y se puede reintentar`() {
        val repo = FakeRepo(fallaCarga = true, categorias = categorias)
        val model = MiLocalViewModel(repo, ubicacion)
        assertEquals(MiLocalUiState.Error(R.string.mi_local_error_cargar), model.uiState.value)
        repo.fallaCarga = false
        model.onReintentar()
        assertIs<MiLocalUiState.Success>(model.uiState.value)
    }

    @Test fun `no guarda si faltan datos obligatorios`() {
        val repo = FakeRepo(categorias = categorias)
        val model = MiLocalViewModel(repo, ubicacion)
        model.onGuardar()
        val errores = model.success().errores
        assertEquals(R.string.mi_local_error_obligatorio, errores.nombre)
        assertEquals(R.string.mi_local_error_categoria, errores.categoria)
        assertEquals(R.string.mi_local_error_obligatorio, errores.comuna)
        assertEquals(R.string.mi_local_error_obligatorio, errores.direccion)
        assertTrue(repo.guardados.isEmpty())
    }

    @Test fun `registra el local nuevo con los datos limpios`() = runTest {
        val repo = FakeRepo(categorias = categorias)
        val model = MiLocalViewModel(repo, ubicacion)
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
        assertEquals("nuevo-1", model.success().localId)
        assertEquals(R.string.mi_local_creado, model.mensajes.first())
    }

    @Test fun `edita el local existente`() = runTest {
        val repo = FakeRepo(local = existente, categorias = categorias)
        val model = MiLocalViewModel(repo, ubicacion)
        model.onCambio { copy(horario = "Lun a Dom 10:00–21:00") }
        model.onGuardar()
        assertEquals("local-1", repo.guardados.single().first)
        assertEquals("Lun a Dom 10:00–21:00", model.success().formulario.horario)
        assertEquals(R.string.mi_local_guardado, model.mensajes.first())
    }

    @Test fun `si falla el guardado conserva lo escrito y avisa`() = runTest {
        val repo = FakeRepo(local = existente, fallaGuardado = true, categorias = categorias)
        val model = MiLocalViewModel(repo, ubicacion)
        model.onCambio { copy(nombre = "Nuevo nombre") }
        model.onGuardar()
        assertEquals("Nuevo nombre", model.success().formulario.nombre)
        assertFalse(model.success().guardando)
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
        val repo = FakeRepo(local = existente, categorias = categorias)
        val model = MiLocalViewModel(repo, ubicacion)
        model.onUsarUbicacion()
        ubicacion.estado.value = EstadoUbicacion(cargando = true)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 10, lat = -33.0425, lon = -71.6285))
        assertEquals(-33.0425, model.success().formulario.latitud)
        assertEquals(-71.6285, model.success().formulario.longitud)
        assertEquals(18f, model.success().precisionMetros)
        model.onGuardar()
        assertEquals(-33.0425, repo.guardados.single().second.latitud)
    }

    @Test fun `no toma la ubicacion si el oferente no pulso el boton`() {
        val model = MiLocalViewModel(FakeRepo(categorias = categorias), ubicacion)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 10))
        assertNull(model.success().formulario.latitud)
    }

    @Test fun `acepta la misma posicion en cache si la consulta pedida ya empezo`() {
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 5))
        val model = MiLocalViewModel(FakeRepo(categorias = categorias), ubicacion)
        model.onUsarUbicacion()
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 5), cargando = true)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 5))
        assertEquals(-33.04, model.success().formulario.latitud)
    }

    @Test fun `un error de ubicacion cancela la espera`() {
        val model = MiLocalViewModel(FakeRepo(categorias = categorias), ubicacion)
        model.onUsarUbicacion()
        ubicacion.estado.value = EstadoUbicacion(problema = LocationProblem.PERMISSION_REQUIRED)
        ubicacion.estado.value = EstadoUbicacion(posicion = fix(ts = 10))
        assertNull(model.success().formulario.latitud)
    }
}
