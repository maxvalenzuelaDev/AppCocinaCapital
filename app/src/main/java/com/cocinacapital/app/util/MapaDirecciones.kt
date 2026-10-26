package com.cocinacapital.app.util

import com.cocinacapital.app.data.model.ModoRuta
import com.cocinacapital.app.data.model.PuntoRuta

fun urlDirecciones(destino: PuntoRuta, origen: PuntoRuta? = null, modo: ModoRuta? = null): String =
    "https://www.google.com/maps/dir/?api=1&destination=${destino.latitud},${destino.longitud}" +
        (origen?.let { "&origin=${it.latitud},${it.longitud}" } ?: "") +
        (modo?.let { "&travelmode=${it.maps}" } ?: "") + "&dir_action=navigate"
