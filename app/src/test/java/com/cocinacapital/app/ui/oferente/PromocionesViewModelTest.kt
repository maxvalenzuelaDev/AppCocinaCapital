package com.cocinacapital.app.ui.oferente

import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.DatosPromocion
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

    private class FakePromos(var falla: Boolean = false) : PromocionesOferenteRepository {
        val creadas = mutableListOf<DatosPromocion>()
        override suspend fun listar(localId: String) = emptyList<Promocion>()
        override suspend fun crear(localId: String, datos: DatosPromocion): Promocion {
            if (falla) error("rechazado")
            creadas += datos
            return Promocion(
                id = "promo-${creadas.size}", localId = localId, titulo = datos.titulo,
                descuentoPorcentaje = datos.descuentoPorcentaje, vigenteDesde = datos.vigenteDesde,
                vigenteHasta = datos.vigenteHasta, cuposTotal = datos.cuposTotal, cuposDisponibles = datos.cuposTotal
            )
        }
    }

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
}
