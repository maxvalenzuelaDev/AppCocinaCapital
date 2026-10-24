package com.cocinacapital.app.data.model

import com.cocinacapital.app.util.LocaleCL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat

/** Fila de la tabla `platos` (menú de un local). [precio] en pesos chilenos, sin decimales. */
@Serializable
data class Plato(
    val id: String,
    @SerialName("local_id") val localId: String,
    val nombre: String,
    val descripcion: String = "",
    val precio: Int,
    val destacado: Boolean = false
)

/** Precio para mostrar, ej: "$12.900". */
fun Plato.precioTexto(): String =
    NumberFormat.getCurrencyInstance(LocaleCL).apply { maximumFractionDigits = 0 }.format(precio)
