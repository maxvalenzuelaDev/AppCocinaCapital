package com.cocinacapital.app.ui.oferente

import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.DatosPromocion
import com.cocinacapital.app.data.repository.MetricasPromocion
import com.cocinacapital.app.data.repository.PromocionesOferenteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PromocionesViewModelTest {
    private val zona = ZoneId.of("America/Santiago")
    private val hoy = LocalDate.of(2026, 10, 27)
    private val reloj = Clock.fixed(hoy.atTime(12, 0).atZone(zona).toInstant(), zona)
    private val local = LocalGastronomico(id = "local-1", nombre = "Café Cerro Alegre", categoriaId = "cat-1", comuna = "Valparaíso")

    private class FakePromos(var falla: Boolean = false, guardadas: List<Promocion> = emptyList()) : PromocionesOferenteRepository {
        val creadas = mutableListOf<DatosPromocion>()
        val guardadas = guardadas.toMutableList()
        val editadas = mutableListOf<Pair<String, DatosPromocion>>()
        val vigencias = mutableListOf<Pair<String?, String>>()
        var metricasCon: suspend (String) -> Map<String, MetricasPromocion> = { emptyMap() }
        override suspend fun metricas(localId: String) = metricasCon(localId)
        override suspend fun listar(localId: String) = guardadas.filter { it.localId == localId }
        override suspend fun crear(localId: String, datos: DatosPromocion): Promocion {
            if (falla) error("rechazado")
            creadas += datos
            return Promocion(
                id = "promo-${creadas.size}", localId = localId, titulo = datos.titulo,
                descuentoPorcentaje = datos.descuentoPorcentaje, vigenteDesde = datos.vigenteDesde,
                vigenteHasta = datos.vigenteHasta, cuposTotal = datos.cuposTotal, cuposDisponibles = datos.cuposTotal
            )
        }
        private fun cambiar(id: String, cambio: (Promocion) -> Promocion): Promocion {
            if (falla) error("rechazado")
            val i = guardadas.indexOfFirst { it.id == id }
            return cambio(guardadas[i]).also { guardadas[i] = it }
        }
        override suspend fun actualizar(promocionId: String, datos: DatosPromocion) = cambiar(promocionId) {
            editadas += promocionId to datos
            it.copy(titulo = datos.titulo, descuentoPorcentaje = datos.descuentoPorcentaje,
                vigenteDesde = datos.vigenteDesde, vigenteHasta = datos.vigenteHasta)
        }
        override suspend fun cambiarActiva(promocionId: String, activa: Boolean) = cambiar(promocionId) { it.copy(activa = activa) }
        override suspend fun cambiarVigencia(promocionId: String, vigenteDesde: String?, vigenteHasta: String) =
            cambiar(promocionId) {
                vigencias += vigenteDesde to vigenteHasta
                it.copy(vigenteDesde = vigenteDesde ?: it.vigenteDesde, vigenteHasta = vigenteHasta)
            }
    }

    /** Vigente desde el 20 de octubre (ya empezó) hasta el 10 de noviembre, de lunes a viernes de 15:00 a 18:00. */
    private val vigente = Promocion(
        id = "p-1", localId = "local-1", titulo = "2x1 en café", descuentoPorcentaje = 50,
        vigenteDesde = "2026-10-20T00:00:00-03:00", vigenteHasta = "2026-11-10T23:59:59-03:00",
        diasSemana = listOf(1, 2, 3, 4, 5), horaInicio = "15:00:00", horaFin = "18:00:00", cuposTotal = 30, cuposDisponibles = 12
    )
    private val programada = vigente.copy(id = "p-2", vigenteDesde = "2026-11-01T00:00:00-03:00")

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** Como lo usa la pantalla: el ViewModel recibe el local elegido en Mis locales. */
    private fun modelo(promos: FakePromos = FakePromos(), localId: String? = local.id) =
        PromocionesViewModel(promos, reloj).also { it.mostrarLocal(localId) }

    private fun PromocionesViewModel.success() = uiState.value as PromocionesUiState.Success

    private fun hora(iso: String): LocalDateTime = OffsetDateTime.parse(iso).atZoneSameInstant(zona).toLocalDateTime()

    @Test fun `sin locales pide registrar uno y al elegirlo carga sus promociones`() {
        val model = modelo(localId = null)
        assertEquals(PromocionesUiState.SinLocal, model.uiState.value)
        model.mostrarLocal("local-2")
        assertEquals("local-2", model.success().localId)
    }

    @Test fun `publica la promocion en el local elegido`() {
        val promos = FakePromos()
        val model = modelo(promos, localId = "local-2")
        model.onNueva()
        model.onCambio { copy(titulo = "2x1", descuento = "50") }
        model.onGuardar()
        assertEquals("local-2", model.success().promociones.single().localId)
    }

    @Test fun `la nueva promocion sugiere una semana desde hoy`() {
        val model = modelo()
        model.onNueva()
        assertEquals(hoy, model.success().formulario!!.desde)
        assertEquals(hoy.plusDays(7), model.success().formulario!!.hasta)
    }

    @Test fun `publica la promocion con vigencia dias franja y cupos`() = runTest {
        val promos = FakePromos()
        val model = modelo(promos)
        model.onNueva()
        model.onCambio {
            copy(
                titulo = " 2x1 en café ", descuento = "50%", condiciones = "No acumulable",
                hasta = hoy.plusDays(6), dias = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
                conHorario = true, horaInicio = LocalTime.of(15, 0), horaFin = LocalTime.of(18, 0), cupos = "50"
            )
        }
        model.onGuardar()
        val datos = promos.creadas.single()
        assertEquals("2x1 en café", datos.titulo)
        assertEquals(50, datos.descuentoPorcentaje)
        assertEquals(hoy.atStartOfDay(), hora(datos.vigenteDesde))
        assertEquals(hoy.plusDays(6).atTime(23, 59, 59), hora(datos.vigenteHasta))
        assertEquals(listOf(1, 5), datos.diasSemana)
        assertEquals("15:00", datos.horaInicio)
        assertEquals("18:00", datos.horaFin)
        assertEquals(50, datos.cuposTotal)
        assertNull(model.success().formulario)
        assertEquals("promo-1", model.success().promociones.first().id)
        assertEquals(R.string.promo_creada, model.mensajes.first())
    }

    @Test fun `todos los dias sin franja y sin cupos se guardan como sin restriccion`() {
        val promos = FakePromos()
        val model = modelo(promos)
        model.onNueva()
        model.onCambio {
            copy(titulo = "Happy hour", descuento = "20", dias = DayOfWeek.entries.toSet(), horaInicio = LocalTime.of(9, 0))
        }
        model.onGuardar()
        val datos = promos.creadas.single()
        assertNull(datos.diasSemana)
        assertNull(datos.horaInicio)
        assertNull(datos.cuposTotal)
    }

    @Test fun `no publica con campos obligatorios vacios`() = runTest {
        val promos = FakePromos()
        val model = modelo(promos)
        model.onNueva()
        model.onGuardar()
        val errores = model.success().errores
        assertEquals(R.string.mi_local_error_obligatorio, errores.titulo)
        assertEquals(R.string.promo_error_descuento, errores.descuento)
        assertTrue(promos.creadas.isEmpty())
        assertEquals(R.string.promo_revisa_campos, model.mensajes.first())
    }

    @Test fun `valida fechas horario cupos y descuento`() {
        fun errores(f: FormularioPromocion) = validarPromocion(
            f.copy(titulo = "Promo"), hoy, zona
        ).second
        val base = FormularioPromocion(descuento = "30", desde = hoy, hasta = hoy)
        assertFalse(errores(base).hayErrores)
        assertEquals(R.string.promo_error_desde_pasado, errores(base.copy(desde = hoy.minusDays(1))).vigencia)
        assertEquals(R.string.promo_error_hasta_antes, errores(base.copy(desde = hoy.plusDays(2), hasta = hoy.plusDays(1))).vigencia)
        assertEquals(R.string.promo_error_fechas, errores(base.copy(hasta = null)).vigencia)
        assertEquals(R.string.promo_error_horas, errores(base.copy(conHorario = true)).horario)
        assertEquals(
            R.string.promo_error_horario,
            errores(base.copy(conHorario = true, horaInicio = LocalTime.of(18, 0), horaFin = LocalTime.of(15, 0))).horario
        )
        assertEquals(R.string.promo_error_cupos, errores(base.copy(cupos = "0")).cupos)
        assertEquals(R.string.promo_error_cupos, errores(base.copy(cupos = "veinte")).cupos)
        assertEquals(R.string.promo_error_descuento, errores(base.copy(descuento = "120")).descuento)
    }

    @Test fun `si falla la publicacion el formulario sigue abierto`() = runTest {
        val model = modelo(FakePromos(falla = true))
        model.onNueva()
        model.onCambio { copy(titulo = "2x1", descuento = "50") }
        model.onGuardar()
        assertEquals("2x1", model.success().formulario!!.titulo)
        assertFalse(model.success().guardando)
        assertEquals(R.string.promo_error_guardar, model.mensajes.first())
    }

    @Test fun `calcula el estado de cada promocion`() {
        val ahora = reloj.instant()
        fun promo(desde: String, hasta: String, activa: Boolean = true, disponibles: Int? = null) = Promocion(
            id = "p", localId = "l", titulo = "t", descuentoPorcentaje = 10, vigenteDesde = desde, vigenteHasta = hasta,
            activa = activa, cuposTotal = disponibles?.let { 10 }, cuposDisponibles = disponibles
        )
        assertEquals(EstadoPromocion.VIGENTE, promo("2026-10-01T00:00:00Z", "2026-11-30T00:00:00Z").estado(ahora))
        assertEquals(EstadoPromocion.PROGRAMADA, promo("2026-11-01T00:00:00Z", "2026-11-30T00:00:00Z").estado(ahora))
        assertEquals(EstadoPromocion.VENCIDA, promo("2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z").estado(ahora))
        assertEquals(EstadoPromocion.INACTIVA, promo("2026-10-01T00:00:00Z", "2026-11-30T00:00:00Z", activa = false).estado(ahora))
        assertEquals(EstadoPromocion.AGOTADA, promo("2026-10-01T00:00:00Z", "2026-11-30T00:00:00Z", disponibles = 0).estado(ahora))
    }

    // ---------------- HU-28 ----------------

    @Test fun `editar carga los datos guardados en el formulario`() {
        val model = modelo(FakePromos(guardadas = listOf(vigente)))
        model.onEditar(vigente)
        val f = model.success().formulario!!
        assertEquals("p-1", f.promocionId)
        assertEquals(LocalDate.of(2026, 10, 20), f.desde)
        assertEquals(LocalDate.of(2026, 10, 20), f.desdeOriginal)
        assertEquals(LocalDate.of(2026, 11, 10), f.hasta)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), f.dias)
        assertTrue(f.conHorario)
        assertEquals(LocalTime.of(15, 0), f.horaInicio)
        assertEquals("30", f.cupos)
    }

    @Test fun `edita una promocion que ya empezo conservando su inicio`() = runTest {
        val promos = FakePromos(guardadas = listOf(vigente))
        val model = modelo(promos)
        model.onEditar(vigente)
        model.onCambio { copy(titulo = "3x2 en café", hasta = hoy.plusDays(30)) }
        model.onGuardar()
        val (id, datos) = promos.editadas.single()
        assertEquals("p-1", id)
        assertEquals(LocalDate.of(2026, 10, 20).atStartOfDay(), hora(datos.vigenteDesde))
        assertNull(model.success().formulario)
        assertEquals("3x2 en café", model.success().promociones.single().titulo)
        assertEquals(R.string.promo_editada, model.mensajes.first())
    }

    @Test fun `al editar no se puede mover el inicio a otro dia pasado ni terminar antes de hoy`() {
        val base = vigente.aFormulario(zona)
        fun errores(f: FormularioPromocion) = validarPromocion(f, hoy, zona).second
        assertFalse(errores(base).hayErrores)
        assertEquals(R.string.promo_error_desde_pasado, errores(base.copy(desde = LocalDate.of(2026, 10, 21))).vigencia)
        assertEquals(R.string.promo_error_hasta_pasado, errores(base.copy(hasta = hoy.minusDays(1))).vigencia)
    }

    @Test fun `pausar y reanudar cambian si el consumidor la ve`() = runTest {
        val promos = FakePromos(guardadas = listOf(vigente))
        val model = modelo(promos)
        model.onPausar(vigente)
        assertFalse(model.success().promociones.single().activa)
        assertEquals(EstadoPromocion.INACTIVA, model.success().promociones.single().estado(model.ahora))
        assertNull(model.success().procesandoId)
        assertEquals(R.string.promo_pausada, model.mensajes.first())
        model.onReanudar(model.success().promociones.single())
        assertTrue(model.success().promociones.single().activa)
        assertEquals(R.string.promo_reanudada, model.mensajes.first())
    }

    @Test fun `finalizar una vigente termina su vigencia ahora tras confirmar`() = runTest {
        val promos = FakePromos(guardadas = listOf(vigente))
        val model = modelo(promos)
        model.onPedirFinalizar(vigente)
        assertEquals(vigente, model.success().porFinalizar)
        model.onConfirmarFinalizar()
        val (desde, hasta) = promos.vigencias.single()
        assertNull(desde)
        assertEquals(hoy.atTime(12, 0), hora(hasta))
        assertNull(model.success().porFinalizar)
        assertEquals(EstadoPromocion.VENCIDA, model.success().promociones.single().estado(model.ahora.plusSeconds(1)))
        assertEquals(R.string.promo_finalizada, model.mensajes.first())
    }

    @Test fun `finalizar una programada adelanta tambien su inicio`() {
        val promos = FakePromos(guardadas = listOf(programada))
        val model = modelo(promos)
        model.onPedirFinalizar(programada)
        model.onConfirmarFinalizar()
        val (desde, hasta) = promos.vigencias.single()
        assertTrue(hora(desde!!).isBefore(hora(hasta)))
    }

    @Test fun `cancelar finalizar no cambia nada`() {
        val promos = FakePromos(guardadas = listOf(vigente))
        val model = modelo(promos)
        model.onPedirFinalizar(vigente)
        model.onCancelarFinalizar()
        assertNull(model.success().porFinalizar)
        assertTrue(promos.vigencias.isEmpty())
    }

    @Test fun `si falla pausar la promocion queda igual y avisa`() = runTest {
        val model = modelo(FakePromos(falla = true, guardadas = listOf(vigente)))
        model.onPausar(vigente)
        assertTrue(model.success().promociones.single().activa)
        assertNull(model.success().procesandoId)
        assertEquals(R.string.promo_error_cambiar, model.mensajes.first())
    }

    // ---- HU-30: métricas ----

    @Test fun `muestra los contadores de cada promocion`() {
        val promo = Promocion("p1", local.id, "Combo", descuentoPorcentaje = 20, vigenteHasta = "2030-01-01T00:00:00Z")
        val promos = FakePromos(guardadas = listOf(promo)).apply {
            metricasCon = { mapOf("p1" to MetricasPromocion("p1", vistas = 12, obtenidos = 5, canjeados = 3)) }
        }
        val vm = modelo(promos)
        assertEquals(MetricasPromocion("p1", 12, 5, 3), vm.success().metricas["p1"])
    }

    @Test fun `si fallan las metricas la lista se sigue viendo`() {
        val promo = Promocion("p1", local.id, "Combo", descuentoPorcentaje = 20, vigenteHasta = "2030-01-01T00:00:00Z")
        val promos = FakePromos(guardadas = listOf(promo)).apply { metricasCon = { error("sin red") } }
        val vm = modelo(promos)
        assertEquals(listOf("p1"), vm.success().promociones.map { it.id })
        assertTrue(vm.success().metricas.isEmpty())
    }
}
