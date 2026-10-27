package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Servidor local: verifica la consulta real de Postgrest sin usar el proyecto compartido. */
class SupabaseDetallePromocionRepositoryTest {
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    private lateinit var client: SupabaseClient
    private lateinit var repository: SupabaseDetallePromocionRepository
    private val consultas = ConcurrentLinkedQueue<String>()
    @Volatile private var respuesta = "[]"
    @Volatile private var estadoHttp = "200 OK"
    private val id = "39f24af5-008c-4de5-ae10-2103e0b8d092"
    private val locales = object : DetalleLocalRepository {
        override suspend fun getLocal(id: String): LocalGastronomico? = null
        override suspend fun getPlatosDestacados(localId: String): List<Plato> = emptyList()
        override suspend fun getPromocionesVigentes(localId: String): List<Promocion> = emptyList()
    }

    @Before fun preparar() {
        server = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        worker = thread(isDaemon = true, name = "hu12-http-local") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { responder(it) }
            }
        }
        client = createSupabaseClient("http://127.0.0.1:" + server.localPort, "clave-de-prueba-local") {
            install(Postgrest)
        }
        repository = SupabaseDetallePromocionRepository(client, locales)
    }

    @After fun terminar() = runBlocking {
        client.close()
        server.close()
        worker.join(2_000)
    }

    private fun responder(socket: Socket) {
        socket.soTimeout = 5_000
        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
        val first = reader.readLine() ?: return
        while (true) {
            val header = reader.readLine() ?: break
            if (header.isEmpty()) break
        }
        consultas.add(URLDecoder.decode(first.split(" ")[1].substringAfter("?", ""), "UTF-8"))
        val bytes = respuesta.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $estadoHttp\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    @Test fun `consulta por id una promocion vencida sin ocultarla con el filtro de vigencia`() = runBlocking {
        respuesta = """[{"id":"$id","local_id":"otro","titulo":"Oferta anterior","descuento_porcentaje":25,"vigente_desde":"2026-01-01T00:00:00Z","vigente_hasta":"2026-01-02T00:00:00Z","condiciones":"Solo en local","dias_semana":[2],"hora_inicio":"15:00:00","hora_fin":"18:00:00","cupos_total":10,"cupos_disponibles":0}]"""
        val detalle = repository.getPromocion(id)!!
        val result = detalle.promocion
        assertEquals("Solo en local", result.condiciones)
        assertEquals(0, result.cuposDisponibles)
        assertTrue(detalle.cuposConfigurados)
        assertTrue(detalle.horarioConfigurado)
        val query = consultas.single()
        assertTrue(query.contains("id=eq.$id"))
        assertFalse(query.contains("vigente_hasta="))
        assertFalse(query.contains("activa="))
    }

    @Test fun `la respuesta de una base sin migrar sigue siendo legible`() = runBlocking {
        respuesta = """[{"id":"$id","local_id":"otro","titulo":"Oferta","descuento_porcentaje":25,"vigente_hasta":"2026-11-01T00:00:00Z"}]"""
        val detalle = repository.getPromocion(id)!!
        val result = detalle.promocion
        assertNull(result.cuposDisponibles)
        assertTrue(result.condiciones.isEmpty())
        assertNull(result.horaInicio)
        assertNull(result.horaFin)
        assertFalse(detalle.cuposConfigurados)
        assertFalse(detalle.horarioConfigurado)
    }

    @Test fun `una promocion ausente devuelve null`() = runBlocking {
        assertNull(repository.getPromocion(id))
    }

    @Test fun `el rechazo de permisos no se confunde con una promocion inexistente`() = runBlocking {
        estadoHttp = "403 Forbidden"
        respuesta = """{"code":"42501","message":"permission denied"}"""
        val fallo = runCatching { repository.getPromocion(id) }.exceptionOrNull()
        assertNotNull(fallo)
        assertEquals(1, consultas.size)
    }

    @Test fun `una respuesta incompleta no se presenta como detalle valido`() = runBlocking {
        respuesta = """[{"id":"$id","titulo":"Oferta sin local ni vigencia"}]"""
        assertNotNull(runCatching { repository.getPromocion(id) }.exceptionOrNull())
    }

    @Test fun `un id invalido no envia consultas a Supabase`() = runBlocking {
        assertNull(repository.getPromocion("no-es-un-id"))
        assertNull(repository.getPromocion("1-1-1-1-1"))
        assertTrue(consultas.isEmpty())
    }
}
