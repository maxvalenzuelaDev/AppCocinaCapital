package com.cocinacapital.app.data.model

import com.cocinacapital.app.util.LocaleCL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fila de la tabla `promociones`. [localNombre] y [localCategoriaId] se completan en la app. */
@Serializable
data class Promocion(
    val id: String,
    @SerialName("local_id") val localId: String,
    val titulo: String,
    val descripcion: String = "",
    @SerialName("descuento_porcentaje") val descuentoPorcentaje: Int,
    @SerialName("vigente_desde") val vigenteDesde: String? = null,
    @SerialName("vigente_hasta") val vigenteHasta: String,
    val activa: Boolean = true,
    @Transient val localNombre: String = "",
    @Transient val localCategoriaId: String? = null
)

/** Texto de vigencia para la tarjeta, ej: "Hasta lun 12 oct, 18:00". */
fun Promocion.vigenciaTexto(): String = runCatching {
    val fin = OffsetDateTime.parse(vigenteHasta).atZoneSameInstant(ZoneId.systemDefault())
    "Hasta " + fin.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", LocaleCL))
}.getOrDefault("Vigencia por confirmar")
