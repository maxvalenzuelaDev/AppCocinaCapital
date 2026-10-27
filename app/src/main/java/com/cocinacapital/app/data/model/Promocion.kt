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
    // ---- HU-27: lo define el oferente al crearla (migración 2026-10-27_hu27_*) ----
    val condiciones: String = "",
    /** Días en que aplica (1 = lunes … 7 = domingo); `null` = todos los días. */
    @SerialName("dias_semana") val diasSemana: List<Int>? = null,
    /** Franja horaria, ej: "15:00:00"; ambas `null` = todo el horario del local. */
    @SerialName("hora_inicio") val horaInicio: String? = null,
    @SerialName("hora_fin") val horaFin: String? = null,
    /** `null` = sin límite de cupos. Los disponibles los descuenta el canje (T-02/T-03). */
    @SerialName("cupos_total") val cuposTotal: Int? = null,
    @SerialName("cupos_disponibles") val cuposDisponibles: Int? = null,
    @Transient val localNombre: String = "",
    @Transient val localCategoriaId: String? = null,
    @Transient val distanciaKm: Double? = null
)

/** Texto de vigencia para la tarjeta, ej: "Hasta lun 12 oct, 18:00". */
fun Promocion.fechaFinTexto(): String? = runCatching {
    val fin = OffsetDateTime.parse(vigenteHasta).atZoneSameInstant(ZoneId.systemDefault())
    fin.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", LocaleCL))
}.getOrNull()
