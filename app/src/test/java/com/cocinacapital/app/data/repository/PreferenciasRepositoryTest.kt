package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.SesionDePrueba
import com.cocinacapital.app.data.auth.SesionDePrueba.Companion.LUIS
import com.cocinacapital.app.data.local.PreferenciasDaoEnMemoria
import com.cocinacapital.app.data.local.PreferenciasEntity
import com.cocinacapital.app.data.model.PreferenciasUsuario
import com.cocinacapital.app.data.model.RangoPrecio
import com.cocinacapital.app.data.model.TemaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PreferenciasRepositoryTest {
    private val dao = PreferenciasDaoEnMemoria()
    private val sesion = SesionDePrueba()
    private val repo = PreferenciasRepository(dao, sesion)

    private suspend fun actuales() = repo.observar().first()

    @Test fun `una cuenta nueva parte con todo desactivado (privacidad por defecto)`() = runBlocking {
        val prefs = actuales()
        assertEquals(PreferenciasUsuario(), prefs)
        assertFalse(prefs.consentimiento)
        assertNull(prefs.consentimientoFecha)
        assertFalse(prefs.notificaciones)
        assertEquals(RangoPrecio.MEDIO, prefs.rangoPrecio)
        assertEquals(TemaApp.SISTEMA, prefs.tema)
    }

    @Test fun `aceptar el consentimiento guarda la fecha y revocarlo la borra`() = runBlocking {
        val antes = System.currentTimeMillis()
        repo.setConsentimiento(true)
        val aceptado = actuales()
        assertTrue(aceptado.consentimiento)
        assertNotNull(aceptado.consentimientoFecha)
        assertTrue(aceptado.consentimientoFecha!! in antes..System.currentTimeMillis())

        repo.setConsentimiento(false)
        val revocado = actuales()
        assertFalse(revocado.consentimiento)
        assertNull(revocado.consentimientoFecha)
    }

    @Test fun `alternar una categoria la agrega y la quita`() = runBlocking {
        repo.alternarCategoria("c1")
        repo.alternarCategoria("c2")
        assertEquals(setOf("c1", "c2"), actuales().categoriasIds)
        repo.alternarCategoria("c1")
        assertEquals(setOf("c2"), actuales().categoriasIds)
    }

    @Test fun `cada cambio conserva el resto de las preferencias`() = runBlocking {
        repo.setRangoPrecio(RangoPrecio.PREMIUM)
        repo.setNotificaciones(true)
        repo.setSoloCercanas(true)
        repo.setTema(TemaApp.OSCURO)
        val prefs = actuales()
        assertEquals(RangoPrecio.PREMIUM, prefs.rangoPrecio)
        assertTrue(prefs.notificaciones)
        assertTrue(prefs.soloCercanas)
        assertFalse(prefs.soloCategoriasFavoritas)
        assertEquals(TemaApp.OSCURO, prefs.tema)
    }

    @Test fun `las preferencias de una cuenta no se ven en otra`() = runBlocking {
        repo.alternarCategoria("c1")
        repo.setConsentimiento(true)
        sesion.usuario.value = LUIS
        val deLuis = actuales()
        assertTrue(deLuis.categoriasIds.isEmpty())
        assertFalse(deLuis.consentimiento)
    }

    @Test fun `sin sesion se guardan como invitado y no afectan a la cuenta`() = runBlocking {
        sesion.usuario.value = null
        repo.setTema(TemaApp.CLARO)
        assertEquals("CLARO", dao.filas.value[INVITADO]?.tema)
        assertNull(dao.filas.value["ana"])
    }

    @Test fun `la foto devuelve la ruta anterior para borrar su archivo`() = runBlocking {
        assertNull(repo.setFotoPerfil("/fotos/1.jpg"))
        assertEquals("/fotos/1.jpg", repo.setFotoPerfil("/fotos/2.jpg"))
        assertEquals("/fotos/2.jpg", actuales().fotoPerfilRuta)
    }

    @Test fun `valores guardados desconocidos vuelven a los predeterminados`() = runBlocking {
        dao.guardar(PreferenciasEntity(userId = "ana", rangoPrecio = "CARISIMO", tema = "SEPIA"))
        val prefs = actuales()
        assertEquals(RangoPrecio.MEDIO, prefs.rangoPrecio)
        assertEquals(TemaApp.SISTEMA, prefs.tema)
    }
}
