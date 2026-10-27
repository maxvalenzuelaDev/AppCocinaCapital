package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.repository.DatosLocal

/**
 * Estado de "Mis locales" (HU-25): la lista de tiendas del oferente y, si está abierto, el
 * formulario para crear o editar una. Catálogo y Promociones trabajan sobre [Success.seleccionadoId].
 */
sealed interface MisLocalesUiState {
    data object Loading : MisLocalesUiState
    data class Error(@StringRes val mensajeRes: Int) : MisLocalesUiState
    data class Success(
        val locales: List<LocalGastronomico>,
        val categorias: List<Categoria>,
        /** Local elegido para Catálogo y Promociones; `null` si aún no hay locales. */
        val seleccionadoId: String? = null,
        /** Formulario abierto; `null` = se muestra la lista. */
        val editor: EditorLocal? = null
    ) : MisLocalesUiState {
        val seleccionado: LocalGastronomico? get() = locales.firstOrNull { it.id == seleccionadoId }
    }
}

/** Formulario de un local: nuevo si [localId] es `null`. */
data class EditorLocal(
    val localId: String? = null,
    val formulario: FormularioLocal = FormularioLocal(),
    val errores: ErroresLocal = ErroresLocal(),
    val guardando: Boolean = false,
    /** Precisión de la última ubicación tomada con el botón; `null` si la posición vino guardada. */
    val precisionMetros: Float? = null,
    /** Foto de portada actual (URL pública de Storage, T-05); solo un local ya guardado puede tener. */
    val imagenUrl: String? = null,
    val subiendoFoto: Boolean = false
)

/** Texto tal como lo escribe el oferente; se valida y convierte al guardar. */
data class FormularioLocal(
    val nombre: String = "",
    val categoriaId: String? = null,
    val comuna: String = "",
    val direccion: String = "",
    val descripcion: String = "",
    val horario: String = "",
    val ticket: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null
)

data class ErroresLocal(
    @StringRes val nombre: Int? = null,
    @StringRes val categoria: Int? = null,
    @StringRes val comuna: Int? = null,
    @StringRes val direccion: Int? = null,
    @StringRes val descripcion: Int? = null,
    @StringRes val horario: Int? = null,
    @StringRes val ticket: Int? = null
) {
    val hayErrores: Boolean
        get() = listOf(nombre, categoria, comuna, direccion, descripcion, horario, ticket).any { it != null }
}

internal const val MAX_NOMBRE = 80
internal const val MAX_COMUNA = 60
internal const val MAX_DIRECCION = 120
internal const val MAX_HORARIO = 120
internal const val MAX_DESCRIPCION = 500
private const val MAX_TICKET = 1_000_000L

/**
 * Valida el formulario. Devuelve los datos listos para Supabase, o `null` y los errores por campo.
 * Nombre, categoría, comuna y dirección son obligatorios (sin ellos el consumidor no ubica el local).
 */
fun validarLocal(formulario: FormularioLocal, categorias: List<Categoria>): Pair<DatosLocal?, ErroresLocal> {
    val nombre = formulario.nombre.trim()
    val comuna = formulario.comuna.trim()
    val direccion = formulario.direccion.trim()
    val descripcion = formulario.descripcion.trim()
    val horario = formulario.horario.trim()
    val ticket = ticketDesde(formulario.ticket)
    val errores = ErroresLocal(
        nombre = when {
            nombre.isEmpty() -> R.string.mi_local_error_obligatorio
            nombre.length > MAX_NOMBRE -> R.string.mi_local_error_largo
            else -> null
        },
        categoria = R.string.mi_local_error_categoria.takeIf { categorias.none { it.id == formulario.categoriaId } },
        comuna = when {
            comuna.isEmpty() -> R.string.mi_local_error_obligatorio
            comuna.length > MAX_COMUNA -> R.string.mi_local_error_largo
            else -> null
        },
        direccion = when {
            direccion.isEmpty() -> R.string.mi_local_error_obligatorio
            direccion.length > MAX_DIRECCION -> R.string.mi_local_error_largo
            else -> null
        },
        descripcion = R.string.mi_local_error_largo.takeIf { descripcion.length > MAX_DESCRIPCION },
        horario = R.string.mi_local_error_largo.takeIf { horario.length > MAX_HORARIO },
        ticket = R.string.mi_local_error_ticket.takeIf { ticket == MONTO_INVALIDO }
    )
    if (errores.hayErrores) return null to errores
    return DatosLocal(
        nombre = nombre,
        categoriaId = formulario.categoriaId!!,
        comuna = comuna,
        direccion = direccion,
        descripcion = descripcion,
        horario = horario.ifEmpty { null },
        ticketPromedioPersona = ticket,
        latitud = formulario.latitud,
        longitud = formulario.longitud
    ) to errores
}

internal const val MONTO_INVALIDO = -1L

/**
 * Monto en pesos escrito por el oferente: acepta "15000", "15.000" o "$15.000" (como se muestran los
 * precios en la app). Vacío = `null`; cualquier otra cosa, o un monto fuera de 1..[maximo], devuelve
 * [MONTO_INVALIDO]. Se usa para el consumo por persona (HU-25) y el precio de los platos (HU-26).
 */
internal fun montoEnPesos(texto: String, maximo: Long): Long? {
    val limpio = texto.trim().removePrefix("$").replace(".", "").replace(" ", "")
    if (limpio.isEmpty()) return null
    if (!limpio.all(Char::isDigit) || limpio.length > 9) return MONTO_INVALIDO
    return limpio.toLong().takeIf { it in 1..maximo } ?: MONTO_INVALIDO
}

internal fun ticketDesde(texto: String): Long? = montoEnPesos(texto, MAX_TICKET)

fun LocalGastronomico.aFormulario() = FormularioLocal(
    nombre = nombre,
    categoriaId = categoriaId,
    comuna = comuna,
    direccion = direccion.orEmpty(),
    descripcion = descripcion,
    horario = horario.orEmpty(),
    ticket = ticketPromedioPersona?.toString().orEmpty(),
    latitud = latitud,
    longitud = longitud
)
