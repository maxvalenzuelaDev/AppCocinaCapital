package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.SesionDePrueba
import com.cocinacapital.app.data.local.PreferenciasDaoEnMemoria
import com.cocinacapital.app.data.local.PreferenciasEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import java.io.IOException
import kotlin.test.*

/** T-06: preferencias y consentimiento copiados en Supabase (HU-02, HU-03). */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenciasSincronizacionTest {

    /** Supabase en memoria: una fila por cuenta y el historial de consentimientos. */
    private class Nube : PreferenciasRemotas {
        val filas = mutableMapOf<String, PreferenciasRemotasDto>()
        val consentimientos = mutableListOf<Boolean>()
        var sinRed = false
        override suspend fun obtener(usuarioId: String): PreferenciasRemotasDto? {
            if (sinRed) throw IOException("sin red")
            return filas[usuarioId]
        }
        override suspend fun guardar(preferencias: PreferenciasRemotasDto) {
            if (sinRed) throw IOException("sin red")
            filas[preferencias.usuarioId] = preferencias
        }
        override suspend fun registrarConsentimiento(acepto: Boolean) {
            if (sinRed) throw IOException("sin red")
            consentimientos += acepto
        }
    }

    private val nube = Nube()

    /** Un teléfono: su propia base Room y la misma cuenta. */
    private fun TestScope.telefono(dao: PreferenciasDaoEnMemoria = PreferenciasDaoEnMemoria()) =
        PreferenciasRepository(dao, SesionDePrueba(), nube, this) to dao

    @Test fun `cambiar una preferencia en un telefono se refleja al iniciar sesion en otro`() = runTest {
        val (primero, _) = telefono()
        primero.alternarCategoria("c1")
        primero.setRangoPrecio(RangoPrecio.PREMIUM)
        primero.setTema(TemaApp.OSCURO)
        advanceUntilIdle()

        val (segundo, _) = telefono()
        segundo.sincronizar("ana")
        val prefs = segundo.observar().first()
        assertEquals(setOf("c1"), prefs.categoriasIds)
        assertEquals(RangoPrecio.PREMIUM, prefs.rangoPrecio)
        assertEquals(TemaApp.OSCURO, prefs.tema)
    }

    @Test fun `el consentimiento se sube con su fecha y queda en el historial`() = runTest {
        val (repo, _) = telefono()
        repo.responderConsentimiento("ana", true)
        repo.setConsentimiento(false)
        advanceUntilIdle()
        assertEquals(listOf(true, false), nube.consentimientos)
        assertFalse(nube.filas.getValue("ana").consentimiento)
        assertTrue(nube.filas.getValue("ana").consentimientoRespondido)

        val (otro, _) = telefono()
        otro.sincronizar("ana")
        assertTrue(otro.consentimientoRespondido("ana").first())
    }

    @Test fun `la fecha de aceptacion viaja sin perder el instante`() = runTest {
        val (repo, _) = telefono()
        repo.setConsentimiento(true)
        advanceUntilIdle()
        val fechaLocal = repo.observar().first().consentimientoFecha
        val (otro, _) = telefono()
        otro.sincronizar("ana")
        assertEquals(fechaLocal, otro.observar().first().consentimientoFecha)
    }

    @Test fun `sin red el cambio queda pendiente y se sube al iniciar sesion`() = runTest {
        nube.sinRed = true
        val (repo, dao) = telefono()
        repo.setRangoPrecio(RangoPrecio.ECONOMICO)
        advanceUntilIdle()
        assertTrue(dao.filas.value.getValue("ana").pendienteSincronizar)
        assertTrue(nube.filas.isEmpty())

        nube.sinRed = false
        repo.sincronizar("ana")
        assertEquals("ECONOMICO", nube.filas.getValue("ana").rangoPrecio)
        assertFalse(dao.filas.value.getValue("ana").pendienteSincronizar)
    }

    @Test fun `lo pendiente en el telefono no se pisa con lo descargado`() = runTest {
        nube.filas["ana"] = PreferenciasEntity(userId = "ana", rangoPrecio = "PREMIUM").aRemotas(emptySet())
        nube.sinRed = true
        val (repo, _) = telefono()
        repo.setRangoPrecio(RangoPrecio.ECONOMICO)
        advanceUntilIdle()
        nube.sinRed = false
        repo.sincronizar("ana")
        assertEquals(RangoPrecio.ECONOMICO, repo.observar().first().rangoPrecio)
        assertEquals("ECONOMICO", nube.filas.getValue("ana").rangoPrecio)
    }

    @Test fun `la foto de perfil queda solo en el telefono`() = runTest {
        val (repo, dao) = telefono()
        repo.setFotoPerfil("/fotos/yo.jpg")
        advanceUntilIdle()
        assertTrue(nube.filas.isEmpty())
        assertFalse(dao.filas.value.getValue("ana").pendienteSincronizar)

        nube.filas["ana"] = PreferenciasEntity(userId = "ana", tema = "CLARO").aRemotas(emptySet())
        repo.sincronizar("ana")
        assertEquals("/fotos/yo.jpg", repo.observar().first().fotoPerfilRuta)
        assertEquals(TemaApp.CLARO, repo.observar().first().tema)
    }

    @Test fun `una cuenta nueva sin datos no crea filas vacias`() = runTest {
        val (repo, dao) = telefono()
        repo.sincronizar("ana")
        assertTrue(nube.filas.isEmpty())
        assertTrue(dao.filas.value.isEmpty())
    }

    @Test fun `sin red al iniciar sesion la app sigue con lo del telefono`() = runTest {
        val (repo, _) = telefono()
        repo.setTema(TemaApp.OSCURO)
        advanceUntilIdle()
        nube.sinRed = true
        assertFailsWith<IOException> { repo.sincronizar("ana") }
        assertEquals(TemaApp.OSCURO, repo.observar().first().tema)
    }

    @Test fun `volver a los valores por defecto tambien se envia a Supabase`() {
        // Misma configuración que supabase-kt: encodeDefaults en false.
        val json = Json { ignoreUnknownKeys = true }
        val fila = json.encodeToJsonElement(PreferenciasEntity(userId = "ana").aRemotas(emptySet())).jsonObject
        for (campo in listOf("categorias", "rango_precio", "notificaciones", "solo_cercanas",
            "solo_categorias_favoritas", "tema", "consentimiento", "consentimiento_fecha", "consentimiento_respondido")) {
            assertTrue(campo in fila, campo)
        }
        assertEquals(JsonNull, fila["consentimiento_fecha"])
    }
}
