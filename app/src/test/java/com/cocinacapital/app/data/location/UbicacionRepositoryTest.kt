package com.cocinacapital.app.data.location

import com.cocinacapital.location.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

class FuenteUbicacionPrueba : LocationSource {
    var permiso = LocationAccess.PRECISE
    var activa = true
    var muestras = listOf(muestra())
    var flujo: Flow<LocationSample> = emptyFlow()
    override fun access() = permiso
    override fun locationEnabled() = activa
    override fun providerAvailable() = true
    override fun cachedLocations() = muestras
    override fun updates() = flujo
    companion object { fun muestra() = LocationSample(-33.0245, -71.5518, 10f, 100L, 1800000000000L, "gps") }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionRepositoryTest {
    @Test fun startsWithoutCoordinatesAndClearsAfterPermissionRevocation() = runTest {
        val fuente = FuenteUbicacionPrueba()
        val repo = AndroidUbicacionRepository(fuente) { 100L }
        assertNull(repo.estado.value.posicion)
        repo.obtener()
        assertNotNull(repo.estado.value.posicion)
        fuente.permiso = LocationAccess.NONE
        repo.validarDisponibilidad()
        assertNull(repo.estado.value.posicion)
    }
    @Test fun disabledGpsClearsPreviouslyObtainedCoordinates() = runTest {
        val fuente = FuenteUbicacionPrueba()
        val repo = AndroidUbicacionRepository(fuente) { 100L }
        repo.obtener(); fuente.activa = false; repo.validarDisponibilidad()
        assertNull(repo.estado.value.posicion)
    }
    @Test fun lateResultCannotRestoreCoordinatesAfterSessionIsCleared() = runTest {
        val gate = CompletableDeferred<LocationSample>()
        val fuente = FuenteUbicacionPrueba().apply { muestras = emptyList(); flujo = flow { emit(gate.await()) } }
        val repo = AndroidUbicacionRepository(fuente) { 100L }
        val job = launch { repo.obtener() }; runCurrent()
        assertTrue(repo.estado.value.cargando)
        repo.limpiar(); gate.complete(FuenteUbicacionPrueba.muestra()); job.join()
        assertNull(repo.estado.value.posicion)
        assertFalse(repo.estado.value.cargando)
    }
    @Test fun explicitDenialRemovesThePositionAndExplainsTheState() = runTest {
        val repo = AndroidUbicacionRepository(FuenteUbicacionPrueba()) { 100L }
        repo.obtener(); repo.permisoDenegado()
        assertNull(repo.estado.value.posicion)
        assertEquals(LocationProblem.PERMISSION_REQUIRED, repo.estado.value.problema)
    }
    @Test fun enablingGpsInSettingsAllowsANewRequest() = runTest {
        val source = FuenteUbicacionPrueba().apply { activa = false }
        val repo = AndroidUbicacionRepository(source) { 100L }
        repo.obtener(); assertEquals(LocationProblem.LOCATION_DISABLED, repo.estado.value.problema)
        source.activa = true; repo.validarDisponibilidad()
        assertNull(repo.estado.value.problema)
        repo.obtener(); assertNotNull(repo.estado.value.posicion)
    }
    @Test fun grantingPermissionInSettingsAllowsANewRequest() = runTest {
        val source = FuenteUbicacionPrueba().apply { permiso = LocationAccess.NONE }
        val repo = AndroidUbicacionRepository(source) { 100L }
        repo.obtener(); assertEquals(LocationProblem.PERMISSION_REQUIRED, repo.estado.value.problema)
        source.permiso = LocationAccess.PRECISE; repo.validarDisponibilidad()
        assertNull(repo.estado.value.problema)
        repo.obtener(); assertNotNull(repo.estado.value.posicion)
    }
    @Test fun denialMessageSurvivesTheResumeAfterThePermissionDialog() {
        val source = FuenteUbicacionPrueba().apply { permiso = LocationAccess.NONE }
        val repo = AndroidUbicacionRepository(source) { 100L }
        repo.permisoDenegado(); repo.validarDisponibilidad()
        assertEquals(LocationProblem.PERMISSION_REQUIRED, repo.estado.value.problema)
    }
}
