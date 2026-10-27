package com.cocinacapital.app.data.model

import com.cocinacapital.app.util.LocaleCL
import java.text.NumberFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Propuesta culinaria independiente de los platos y de los descuentos. Precio en CLP. */
@Serializable
data class Experiencia(
    val id: String,
    @SerialName("local_id") val localId: String,
    val titulo: String,
    val descripcion: String = "",
    val horario: String? = null,
    @SerialName("precio_referencial") val precioReferencial: Long? = null,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    val activa: Boolean = true,
    @SerialName("locales") val local: LocalGastronomico? = null
)

fun Experiencia.precioReferencialTexto(): String? = precioReferencial?.takeIf { it >= 0 }?.let {
    NumberFormat.getCurrencyInstance(LocaleCL).apply { maximumFractionDigits = 0 }.format(it)
}
