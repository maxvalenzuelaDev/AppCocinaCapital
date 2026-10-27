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
class SupabaseExperienciasRepositoryTest {
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    private lateinit var client: SupabaseClient
    private lateinit var repository: SupabaseExperienciasRepository
    private val consultas = ConcurrentLinkedQueue<String>()
    @Volatile private var respuesta = "[]"
    @Volatile private var estadoHttp = "200 OK"
    private val id = "39f24af5-008c-4de5-ae10-2103e0b8d092"
    @Before fun preparar() {
        server = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        worker = thread(isDaemon = true, name = "hu09-http-local") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { responder(it) }
            }
        }
        client = createSupabaseClient("http://127.0.0.1:" + server.localPort, "clave-de-prueba-local") {
            install(Postgrest)
        }
        repository = SupabaseExperienciasRepository(client)
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

    private fun fila(activa: Boolean = true, incluirLocal: Boolean = true): String {
        val local = if (incluirLocal) """{"id":"local","nombre":"Comercio","categoria_id":"c","comuna":"Viña del Mar","activo":true}""" else "null"
        return """{"id":"$id","local_id":"local","titulo":"Menú temático","descripcion":"Cuatro tiempos","horario":"Jueves 19:00","precio_referencial":42000,"activa":$activa,"locales":$local}"""
    }

    @Test fun `listado obtiene local en una sola consulta y pide solo publicaciones activas`() = runBlocking {
        respuesta = "[${fila()}]"
        val result = repository.listar().single()
        assertEquals("Comercio", result.local!!.nombre)
        assertEquals(42000L, result.precioReferencial)
        val query = consultas.single()
        assertTrue(query.contains("locales!inner(*)"))
        assertTrue(query.contains("activa=eq.true"))
        assertTrue(query.contains("locales.activo=eq.true"))
        assertTrue(query.contains("titulo.asc"))
    }
    @Test fun `detalle consulta el id exacto y conserva el horario propio`() = runBlocking {
        respuesta = "[${fila()}]"
        assertEquals("Jueves 19:00", repository.getExperiencia(id)!!.horario)
        assertTrue(consultas.single().contains("id=eq.$id"))
    }
    @Test fun `id invalido no consulta el servidor`() = runBlocking {
        assertNull(repository.getExperiencia("no-es-uuid"))
        assertNull(repository.getExperiencia("1-1-1-1-1"))
        assertTrue(consultas.isEmpty())
    }
    @Test fun `catalogo vacio y detalle ausente se representan sin inventar propuestas`() = runBlocking {
        assertTrue(repository.listar().isEmpty())
        assertNull(repository.getExperiencia(id))
    }
    @Test fun `no muestra una experiencia inactiva o sin local asociado`() = runBlocking {
        respuesta = "[${fila(activa = false)},${fila(incluirLocal = false)}]"
        assertTrue(repository.listar().isEmpty())
        respuesta = "[${fila(activa = false)}]"
        assertNull(repository.getExperiencia(id))
    }
    @Test fun `la ausencia de migracion es error y no un catalogo vacio`() = runBlocking {
        estadoHttp = "404 Not Found"
        respuesta = """{"code":"PGRST205","message":"Could not find table experiencias"}"""
        assertNotNull(runCatching { repository.listar() }.exceptionOrNull())
    }
    @Test fun `permisos rechazados permiten que la pantalla informe un error`() = runBlocking {
        estadoHttp = "403 Forbidden"
        respuesta = """{"code":"42501","message":"permission denied"}"""
        assertNotNull(runCatching { repository.getExperiencia(id) }.exceptionOrNull())
    }
}
