package com.cocinacapital.app.data.location

import com.cocinacapital.app.data.auth.LoginSession
import com.cocinacapital.app.data.auth.LoginUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionSesionTest {
    private fun cuenta(id: String = "uno") = LoginSession.SignedIn(LoginUser(id, "prueba@example.com"))

    @Test fun refreshWhileGpsIsPendingDoesNotDiscardThePosition() = runTest {
        val fix = CompletableDeferred<com.cocinacapital.location.LocationSample>()
        val source = FuenteUbicacionPrueba().apply { muestras = emptyList(); flujo = flow { emit(fix.await()) } }
        val gps = AndroidUbicacionRepository(source) { 100L }
        val sessions = MutableSharedFlow<LoginSession>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessions.identidadesUbicacion().collect { gps.limpiar() }
        }
        sessions.emit(cuenta())
        val request = launch { gps.obtener() }; runCurrent()
        sessions.emit(LoginSession.TemporarilyUnavailable)
        sessions.emit(LoginSession.Initializing)
        sessions.emit(cuenta())
        fix.complete(FuenteUbicacionPrueba.muestra()); request.join()
        assertNotNull(gps.estado.value.posicion)
    }

    @Test fun confirmedLogoutStillClearsThePosition() = runTest {
        val gps = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
        val sessions = MutableSharedFlow<LoginSession>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessions.identidadesUbicacion().collect { gps.limpiar() }
        }
        sessions.emit(cuenta()); gps.obtener()
        assertNotNull(gps.estado.value.posicion)
        sessions.emit(LoginSession.SignedOut)
        assertNull(gps.estado.value.posicion)
    }

    @Test fun changingAccountStillClearsThePosition() = runTest {
        val gps = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
        val sessions = MutableSharedFlow<LoginSession>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessions.identidadesUbicacion().collect { gps.limpiar() }
        }
        sessions.emit(cuenta()); gps.obtener()
        sessions.emit(cuenta("dos"))
        assertNull(gps.estado.value.posicion)
    }
}
