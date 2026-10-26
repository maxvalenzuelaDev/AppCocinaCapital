package com.cocinacapital.app.data.model

data class PuntoRuta(val latitud: Double, val longitud: Double) {
    init { require(latitud.isFinite() && latitud in -90.0..90.0 && longitud.isFinite() && longitud in -180.0..180.0) }
}

enum class ModoRuta(val servidor: String, val perfil: String, val maps: String) {
    A_PIE("foot", "foot", "walking"), AUTO("car", "driving", "driving")
}

data class SolicitudRuta(val localId: String, val origen: PuntoRuta, val destino: PuntoRuta, val modo: ModoRuta)
data class Ruta(val puntos: List<PuntoRuta>, val metros: Double, val segundos: Double)
