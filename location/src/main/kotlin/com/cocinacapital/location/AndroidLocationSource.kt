package com.cocinacapital.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Usa LocationManager de Android; funciona sin Google Play Services. */
class AndroidLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun access(): LocationAccess = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.APPROXIMATE
        else -> LocationAccess.NONE
    }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    override fun locationEnabled() = manager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false
    override fun providerAvailable() = providers().isNotEmpty()

    private fun providers(): List<String> {
        val enabled = manager?.getProviders(true).orEmpty()
        return buildList {
            if (access() == LocationAccess.PRECISE && LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
            if (access() != LocationAccess.NONE && LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    override fun cachedLocations(): List<LocationSample> {
        if (access() == LocationAccess.NONE) throw SecurityException()
        return providers().mapNotNull { manager?.getLastKnownLocation(it)?.sample() }
    }

    @SuppressLint("MissingPermission")
    override fun updates() = callbackFlow {
        val service = manager
        if (service == null) { close(IllegalStateException("Servicio de ubicación no disponible")); return@callbackFlow }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { location.sample()?.let { trySend(it) } }
            override fun onProviderDisabled(provider: String) {
                if (!locationEnabled() || providers().isEmpty()) close(LocationTurnedOff())
            }
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Compatibilidad con Android 8–10")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            if (access() == LocationAccess.NONE) throw SecurityException()
            if (!locationEnabled()) throw LocationTurnedOff()
            val available = providers()
            if (available.isEmpty()) throw IllegalStateException("Sin proveedor autorizado")
            available.forEach { service.requestLocationUpdates(it, 1000L, 0f, listener, Looper.getMainLooper()) }
        } catch (error: Exception) { close(error) }
        // Se ejecuta al obtener una posición, al agotar el tiempo o al cancelar la consulta.
        awaitClose { try { service.removeUpdates(listener) } catch (_: SecurityException) { } }
    }

    private fun Location.sample(): LocationSample? = if (!hasAccuracy()) null else LocationSample(
        latitude, longitude, accuracy, elapsedRealtimeNanos / 1_000_000L, time, provider.orEmpty())
}

fun androidLocationRepository(context: Context) = LocationRepository(
    AndroidLocationSource(context), elapsedClock = SystemClock::elapsedRealtime)
