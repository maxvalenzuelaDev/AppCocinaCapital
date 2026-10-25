package com.cocinacapital.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Consulta puntual: no mantiene seguimiento, no persiste coordenadas y no las envía. */
class LocationRepository(
    private val source: LocationSource,
    private val elapsedClock: () -> Long,
    private val timeoutMillis: Long = 25000,
    private val maxAgeMillis: Long = 30000
) {
    init { require(timeoutMillis > 0); require(maxAgeMillis >= 0) }

    suspend fun currentPosition(): LocationResult {
        try {
            if (source.access() == LocationAccess.NONE) return failure(LocationProblem.PERMISSION_REQUIRED)
            if (!source.locationEnabled()) return failure(LocationProblem.LOCATION_DISABLED)
            if (!source.providerAvailable()) return failure(LocationProblem.NO_PROVIDER)
            val recent = source.cachedLocations().filter(::usable).maxByOrNull { it.elapsedRealtimeMillis }
            if (recent != null) return success(recent, fromCache = true)
            val fix = withTimeoutOrNull(timeoutMillis) { source.updates().first(::usable) }
                ?: return failure(LocationProblem.TIMEOUT)
            return success(fix, fromCache = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            return failure(LocationProblem.PERMISSION_REQUIRED)
        } catch (_: LocationTurnedOff) {
            return failure(LocationProblem.LOCATION_DISABLED)
        } catch (_: Exception) {
            return failure(LocationProblem.UNAVAILABLE)
        }
    }

    private fun usable(sample: LocationSample): Boolean {
        val age = elapsedClock() - sample.elapsedRealtimeMillis
        return sample.latitude.isFinite() && sample.latitude in -90.0..90.0 &&
            sample.longitude.isFinite() && sample.longitude in -180.0..180.0 &&
            sample.accuracyMeters.isFinite() && sample.accuracyMeters >= 0 &&
            sample.elapsedRealtimeMillis >= 0 && age in 0..maxAgeMillis && sample.timestampMillis > 0
    }

    private fun success(sample: LocationSample, fromCache: Boolean): LocationResult {
        val permission = source.access()
        if (permission == LocationAccess.NONE) return failure(LocationProblem.PERMISSION_REQUIRED)
        if (!source.locationEnabled()) return failure(LocationProblem.LOCATION_DISABLED)
        return LocationResult.Success(UserPosition(sample.latitude, sample.longitude,
            sample.accuracyMeters, sample.timestampMillis, permission, fromCache))
    }
    private fun failure(problem: LocationProblem) = LocationResult.Failure(problem)
}
