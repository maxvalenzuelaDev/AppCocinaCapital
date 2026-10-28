package com.cocinacapital.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Estado de un código al revisarlo en el local (HU-29). Solo [VALIDO] se puede canjear. */
@Serializable
enum class EstadoCanje {
    @SerialName("valido") VALIDO,
    @SerialName("canjeado") CANJEADO,
    @SerialName("vencido") VENCIDO,
    /** La promoción está pausada o el local inactivo. */
    @SerialName("no_disponible") NO_DISPONIBLE,
    @SerialName("no_iniciado") NO_INICIADO,
    /** Hoy no es uno de sus días o la hora está fuera de su franja (hora de Santiago). */
    @SerialName("fuera_de_horario") FUERA_DE_HORARIO
}

/** Respuesta de `consultar_beneficio` y `canjear_beneficio` (migración T-03). */
@Serializable
data class ResultadoCanje(
    val codigo: String,
    val estado: EstadoCanje,
    @SerialName("canjeado_en") val canjeadoEn: String? = null,
    val titulo: String,
    @SerialName("descuento_porcentaje") val descuentoPorcentaje: Int,
    val condiciones: String = "",
    @SerialName("vigente_hasta") val vigenteHasta: String,
    @SerialName("local_nombre") val localNombre: String,
    /** `true` solo si esta llamada hizo el canje; uno anterior llega como CANJEADO con `false`. */
    @SerialName("canjeado_ahora") val canjeadoAhora: Boolean = false
)
