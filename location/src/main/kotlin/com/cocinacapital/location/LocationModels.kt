package com.cocinacapital.location

enum class LocationAccess { NONE, APPROXIMATE, PRECISE }

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeMillis: Long,
    val timestampMillis: Long,
    val provider: String
)

data class UserPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    val access: LocationAccess,
    val fromCache: Boolean
)

enum class LocationProblem { PERMISSION_REQUIRED, LOCATION_DISABLED, NO_PROVIDER, TIMEOUT, UNAVAILABLE }

sealed interface LocationResult {
    data class Success(val position: UserPosition) : LocationResult
    data class Failure(val problem: LocationProblem) : LocationResult
}

class LocationTurnedOff : Exception()

/** Permite probar la política sin sensores, permisos o dispositivos reales. */
interface LocationSource {
    fun access(): LocationAccess
    fun locationEnabled(): Boolean
    fun providerAvailable(): Boolean
    fun cachedLocations(): List<LocationSample>
    fun updates(): kotlinx.coroutines.flow.Flow<LocationSample>
}
