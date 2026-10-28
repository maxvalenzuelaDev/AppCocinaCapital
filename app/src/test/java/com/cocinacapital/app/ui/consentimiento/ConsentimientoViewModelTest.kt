package com.cocinacapital.app.ui.consentimiento

import com.cocinacapital.app.data.auth.SesionDePrueba
import com.cocinacapital.app.data.local.PreferenciasDaoEnMemoria
import com.cocinacapital.app.data.repository.PreferenciasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** HU-03: pantalla de consentimiento antes del primer uso, por cuenta. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentimientoViewModelTest {
    private val dao = PreferenciasDaoEnMemoria()
    private val sesion = SesionDePrueba()
    private val preferencias = PreferenciasRepository(dao, sesion)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.modelo(usuario: String = "ana"): ConsentimientoViewModel {
        val vm = ConsentimientoViewModel(usuario, preferencias)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.estado.collect {} }
        return vm
    }

    @Test fun `una cuenta nueva ve la pantalla antes de usar la app`() = runTest {
        assertEquals(EstadoConsentimiento.PENDIENTE, modelo().estado.value)
    }

    @Test fun `aceptar guarda el consentimiento con fecha y deja pasar`() = runTest {
        val vm = modelo()
        vm.onAceptar()
        assertEquals(EstadoConsentimiento.RESPONDIDO, vm.estado.value)
        val prefs = preferencias.observar().first()
        assertTrue(prefs.consentimiento)
        assertNotNull(prefs.consentimientoFecha)
        assertTrue(prefs.consentimientoRespondido)
    }

    @Test fun `rechazar tambien deja usar la app pero sin consentimiento`() = runTest {
        val vm = modelo()
        vm.onRechazar()
        assertEquals(EstadoConsentimiento.RESPONDIDO, vm.estado.value)
        val prefs = preferencias.observar().first()
        assertFalse(prefs.consentimiento)
        assertNull(prefs.consentimientoFecha)
        // Al volver a abrir la app no se le pregunta de nuevo.
        assertEquals(EstadoConsentimiento.RESPONDIDO, modelo().estado.value)
    }

    @Test fun `la respuesta es de cada cuenta`() = runTest {
        modelo("ana").onAceptar()
        assertEquals(EstadoConsentimiento.PENDIENTE, modelo("luis").estado.value)
    }

    @Test fun `quien ya lo acepto desde Perfil no vuelve a ver la pantalla`() = runTest {
        preferencias.setConsentimiento(true)
        assertEquals(EstadoConsentimiento.RESPONDIDO, modelo().estado.value)
    }

    @Test fun `revocarlo desde Perfil no vuelve a mostrar la pantalla`() = runTest {
        val vm = modelo()
        vm.onAceptar()
        preferencias.setConsentimiento(false)
        assertEquals(EstadoConsentimiento.RESPONDIDO, vm.estado.value)
        assertFalse(preferencias.observar().first().consentimiento)
    }
}
