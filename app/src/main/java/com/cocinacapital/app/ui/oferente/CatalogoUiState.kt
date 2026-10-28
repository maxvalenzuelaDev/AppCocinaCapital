package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.repository.DatosPlato

/** Estado de la pestaña "Catálogo" del oferente (HU-26). */
sealed interface CatalogoUiState {
    data object Loading : CatalogoUiState
    data class Error(@StringRes val mensajeRes: Int) : CatalogoUiState
    /** El oferente aún no registra su local: primero debe hacerlo en "Mi local". */
    data object SinLocal : CatalogoUiState
    data class Success(
        val localId: String,
        val platos: List<Plato>,
        /** Diálogo para agregar o editar un plato; `null` si está cerrado. */
        val editor: EditorPlato? = null,
        /** Plato que espera confirmación para eliminarse. */
        val porEliminar: Plato? = null,
        val guardando: Boolean = false
    ) : CatalogoUiState
}

/** Formulario del diálogo: texto tal como lo escribe el oferente. */
data class EditorPlato(
    /** `null` = plato nuevo. */
    val platoId: String? = null,
    val nombre: String = "",
    val descripcion: String = "",
    val precio: String = "",
    val destacado: Boolean = false,
    @StringRes val errorNombre: Int? = null,
    @StringRes val errorDescripcion: Int? = null,
    @StringRes val errorPrecio: Int? = null
)

internal const val MAX_NOMBRE_PLATO = 80
internal const val MAX_DESCRIPCION_PLATO = 200
private const val MAX_PRECIO = 1_000_000L

/** Valida el diálogo. Devuelve los datos para Supabase, o `null` y el editor con sus errores. */
fun validarPlato(editor: EditorPlato): Pair<DatosPlato?, EditorPlato> {
    val nombre = editor.nombre.trim()
    val descripcion = editor.descripcion.trim()
    val precio = montoEnPesos(editor.precio, MAX_PRECIO)
    val conErrores = editor.copy(
        errorNombre = when {
            nombre.isEmpty() -> R.string.mi_local_error_obligatorio
            nombre.length > MAX_NOMBRE_PLATO -> R.string.mi_local_error_largo
            else -> null
        },
        errorDescripcion = R.string.mi_local_error_largo.takeIf { descripcion.length > MAX_DESCRIPCION_PLATO },
        errorPrecio = when (precio) {
            null -> R.string.mi_local_error_obligatorio
            MONTO_INVALIDO -> R.string.catalogo_error_precio
            else -> null
        }
    )
    if (conErrores.errorNombre != null || conErrores.errorDescripcion != null || conErrores.errorPrecio != null) {
        return null to conErrores
    }
    return DatosPlato(nombre, descripcion, precio!!.toInt(), editor.destacado) to conErrores
}

fun Plato.aEditor() = EditorPlato(
    platoId = id,
    nombre = nombre,
    descripcion = descripcion,
    precio = precio.toString(),
    destacado = destacado
)
