package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.DatosPromocion
import com.cocinacapital.app.data.repository.MetricasPromocion
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Estado de la pestaña "Promociones" del oferente (HU-27 crear, HU-28 editar, pausar y finalizar). */
sealed interface PromocionesUiState {
    data object Loading : PromocionesUiState
    data class Error(@StringRes val mensajeRes: Int) : PromocionesUiState
    /** Sin local registrado no se pueden crear promociones. */
    data object SinLocal : PromocionesUiState
    data class Success(
        val localId: String,
        val promociones: List<Promocion>,
        /** Formulario de una promoción nueva; `null` = se muestra la lista. */
        val formulario: FormularioPromocion? = null,
        val errores: ErroresPromocion = ErroresPromocion(),
        val guardando: Boolean = false,
        /** Promoción que espera confirmación para finalizarse. */
        val porFinalizar: Promocion? = null,
        /** Promoción que se está pausando, reanudando o finalizando (sus botones se desactivan). */
        val procesandoId: String? = null,
        /** HU-30: contadores por promoción; vacío mientras cargan o si fallaron (no bloquea la lista). */
        val metricas: Map<String, MetricasPromocion> = emptyMap()
    ) : PromocionesUiState
}

/** Lo que el oferente va completando; se valida y convierte al guardar. */
data class FormularioPromocion(
    /** `null` = promoción nueva; si no, la que se está editando (HU-28). */
    val promocionId: String? = null,
    /** Inicio guardado de la promoción que se edita: puede quedar en el pasado si ya empezó. */
    val desdeOriginal: LocalDate? = null,
    val titulo: String = "",
    val descripcion: String = "",
    val condiciones: String = "",
    val descuento: String = "",
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    /** Vacío = todos los días. */
    val dias: Set<DayOfWeek> = emptySet(),
    val conHorario: Boolean = false,
    val horaInicio: LocalTime? = null,
    val horaFin: LocalTime? = null,
    /** Vacío = sin límite. */
    val cupos: String = ""
)

data class ErroresPromocion(
    @StringRes val titulo: Int? = null,
    @StringRes val descripcion: Int? = null,
    @StringRes val condiciones: Int? = null,
    @StringRes val descuento: Int? = null,
    @StringRes val vigencia: Int? = null,
    @StringRes val horario: Int? = null,
    @StringRes val cupos: Int? = null
) {
    val hayErrores: Boolean
        get() = listOf(titulo, descripcion, condiciones, descuento, vigencia, horario, cupos).any { it != null }
}

internal const val MAX_TITULO_PROMO = 60
internal const val MAX_DESCRIPCION_PROMO = 200
internal const val MAX_CONDICIONES_PROMO = 300
private const val MAX_CUPOS = 10_000L

/**
 * Valida el formulario con la fecha de [hoy] y la zona del teléfono. La vigencia va desde el inicio
 * del primer día hasta el final del último, así la promoción se ve todo el día en que termina.
 */
fun validarPromocion(
    formulario: FormularioPromocion,
    hoy: LocalDate,
    zona: ZoneId
): Pair<DatosPromocion?, ErroresPromocion> {
    // Al editar una promoción que ya empezó se conserva su inicio; uno nuevo no puede ser pasado.
    val inicioPermitido = formulario.desde != null && formulario.desde == formulario.desdeOriginal
    val titulo = formulario.titulo.trim()
    val descripcion = formulario.descripcion.trim()
    val condiciones = formulario.condiciones.trim()
    val descuento = formulario.descuento.trim().removeSuffix("%").trim().toIntOrNull()
    val cupos = montoEnPesos(formulario.cupos, MAX_CUPOS)
    val (desde, hasta) = formulario.desde to formulario.hasta
    val errores = ErroresPromocion(
        titulo = when {
            titulo.isEmpty() -> R.string.mi_local_error_obligatorio
            titulo.length > MAX_TITULO_PROMO -> R.string.mi_local_error_largo
            else -> null
        },
        descripcion = R.string.mi_local_error_largo.takeIf { descripcion.length > MAX_DESCRIPCION_PROMO },
        condiciones = R.string.mi_local_error_largo.takeIf { condiciones.length > MAX_CONDICIONES_PROMO },
        descuento = R.string.promo_error_descuento.takeIf { descuento == null || descuento !in 1..100 },
        vigencia = when {
            desde == null || hasta == null -> R.string.promo_error_fechas
            desde.isBefore(hoy) && !inicioPermitido -> R.string.promo_error_desde_pasado
            hasta.isBefore(desde) -> R.string.promo_error_hasta_antes
            hasta.isBefore(hoy) -> R.string.promo_error_hasta_pasado
            else -> null
        },
        horario = when {
            !formulario.conHorario -> null
            formulario.horaInicio == null || formulario.horaFin == null -> R.string.promo_error_horas
            !formulario.horaInicio.isBefore(formulario.horaFin) -> R.string.promo_error_horario
            else -> null
        },
        cupos = R.string.promo_error_cupos.takeIf { cupos == MONTO_INVALIDO }
    )
    if (errores.hayErrores) return null to errores
    val hora = DateTimeFormatter.ofPattern("HH:mm")
    return DatosPromocion(
        titulo = titulo,
        descripcion = descripcion,
        condiciones = condiciones,
        descuentoPorcentaje = descuento!!,
        vigenteDesde = desde!!.atStartOfDay(zona).toOffsetDateTime().toString(),
        vigenteHasta = hasta!!.atTime(LocalTime.of(23, 59, 59)).atZone(zona).toOffsetDateTime().toString(),
        diasSemana = formulario.dias.takeIf { it.isNotEmpty() && it.size < 7 }?.map { it.value }?.sorted(),
        horaInicio = formulario.horaInicio?.takeIf { formulario.conHorario }?.format(hora),
        horaFin = formulario.horaFin?.takeIf { formulario.conHorario }?.format(hora),
        cuposTotal = cupos?.toInt()
    ) to errores
}

/** Estado de una promoción en la lista del oferente. */
enum class EstadoPromocion(@StringRes val etiquetaRes: Int) {
    VIGENTE(R.string.promo_estado_vigente),
    PROGRAMADA(R.string.promo_estado_programada),
    AGOTADA(R.string.promo_estado_agotada),
    VENCIDA(R.string.promo_estado_vencida),
    INACTIVA(R.string.promo_estado_inactiva);

    /** Se puede pausar o finalizar mientras no haya terminado. */
    val enCurso: Boolean get() = this == VIGENTE || this == PROGRAMADA || this == AGOTADA
}

fun Promocion.estado(ahora: Instant): EstadoPromocion {
    val desde = vigenteDesde?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    val hasta = runCatching { OffsetDateTime.parse(vigenteHasta).toInstant() }.getOrNull()
    return when {
        !activa -> EstadoPromocion.INACTIVA
        hasta != null && hasta.isBefore(ahora) -> EstadoPromocion.VENCIDA
        desde != null && desde.isAfter(ahora) -> EstadoPromocion.PROGRAMADA
        cuposDisponibles == 0 -> EstadoPromocion.AGOTADA
        else -> EstadoPromocion.VIGENTE
    }
}

/** Formulario con los datos de una promoción guardada, para editarla (HU-28). */
fun Promocion.aFormulario(zona: ZoneId): FormularioPromocion {
    fun dia(iso: String?) = iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(zona).toLocalDate() }.getOrNull() }
    fun hora(texto: String?) = texto?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val desde = dia(vigenteDesde)
    val inicio = hora(horaInicio)
    val fin = hora(horaFin)
    return FormularioPromocion(
        promocionId = id,
        desdeOriginal = desde,
        titulo = titulo,
        descripcion = descripcion,
        condiciones = condiciones,
        descuento = descuentoPorcentaje.toString(),
        desde = desde,
        hasta = dia(vigenteHasta),
        dias = diasSemana.orEmpty().mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
        conHorario = inicio != null && fin != null,
        horaInicio = inicio,
        horaFin = fin,
        cupos = cuposTotal?.toString().orEmpty()
    )
}
