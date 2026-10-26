package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicLong

interface RutaRepository {
    suspend fun calcular(solicitud: SolicitudRuta): Ruta
    fun limpiar() = Unit
}
class RutaNoEncontrada : Exception()

/** OSRM/FOSSGIS: consultas explícitas, caché en memoria y como máximo una petición por segundo. */
class OsrmRutaRepository(
    private val descargar: suspend (String) -> String,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val baseUrl: String = "https://routing.openstreetmap.de"
) : RutaRepository {
    private data class Cache(val ruta: Ruta, val creada: Long)
    private val consultas = Mutex()
    private var ultimaPeticion: Long? = null
    private val cache = LinkedHashMap<SolicitudRuta, Cache>()
    private val generation = AtomicLong()

    override fun limpiar() {
        synchronized(cache) { generation.incrementAndGet(); cache.clear() }
    }

    override suspend fun calcular(solicitud: SolicitudRuta): Ruta = consultas.withLock {
        val version = generation.get()
        synchronized(cache) { cache[solicitud] }?.takeIf { clock() - it.creada in 0..120_000L && version == generation.get() }
            ?.let { return@withLock it.ruta }
        ultimaPeticion?.let { delay((1_000L - (clock() - it)).coerceAtLeast(0)) }
        if (version != generation.get()) throw CancellationException()
        ultimaPeticion = clock()
        val ruta = interpretarRutaOsrm(descargar(urlRutaOsrm(baseUrl, solicitud)))
        synchronized(cache) {
            if (version != generation.get()) throw CancellationException()
            cache[solicitud] = Cache(ruta, clock())
            if (cache.size > 8) cache.remove(cache.keys.first())
        }
        ruta
    }
}

internal fun urlRutaOsrm(base: String, solicitud: SolicitudRuta): String = with(solicitud) {
    "${base.trimEnd('/')}/routed-${modo.servidor}/route/v1/${modo.perfil}/" +
        "${origen.longitud},${origen.latitud};${destino.longitud},${destino.latitud}" +
        "?overview=full&geometries=geojson&steps=false"
}

internal fun interpretarRutaOsrm(texto: String): Ruta {
    val response = Json.parseToJsonElement(texto).jsonObject
    val code = response["code"]?.jsonPrimitive?.content
    if (code == "NoRoute" || code == "NoSegment") throw RutaNoEncontrada()
    require(code == "Ok")
    val route = response["routes"]!!.jsonArray.first().jsonObject
    val distance = route["distance"]!!.jsonPrimitive.double
    val duration = route["duration"]!!.jsonPrimitive.double
    require(distance.isFinite() && distance >= 0 && duration.isFinite() && duration >= 0)
    val geometry = route["geometry"]!!.jsonObject
    require(geometry["type"]?.jsonPrimitive?.content == "LineString")
    val coords = geometry["coordinates"]!!.jsonArray
    require(coords.size in 2..30_000)
    val puntos = coords.map { value ->
        val coord = value.jsonArray
        require(coord.size >= 2)
        PuntoRuta(latitud = coord[1].jsonPrimitive.double, longitud = coord[0].jsonPrimitive.double)
    }
    return Ruta(puntos, distance, duration)
}
