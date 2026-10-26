package com.cocinacapital.location

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocationRepositoryTest {
    private val now = 100000L
    private fun sample(age: Long = 0) = LocationSample(-33.0245, -71.5518, 15f, now - age, 1800000000000, "gps")
    private class Fake : LocationSource {
        var permission = LocationAccess.PRECISE
        var enabled = true
        var available = true
        var cached = emptyList<LocationSample>()
        var stream: Flow<LocationSample> = flow { awaitCancellation() }
        var subscribed = false
        override fun access() = permission
        override fun locationEnabled() = enabled
        override fun providerAvailable() = available
        override fun cachedLocations() = cached
        override fun updates(): Flow<LocationSample> { subscribed = true; return stream }
    }
    private fun repo(source: Fake) = LocationRepository(source, { now }, timeoutMillis = 1000)
    private fun failure(problem: LocationProblem) = LocationResult.Failure(problem)

    @Test fun noPermissionDoesNotStartSensors() = runTest {
        val source = Fake().apply { permission = LocationAccess.NONE }
        assertEquals(failure(LocationProblem.PERMISSION_REQUIRED), repo(source).currentPosition())
        assertFalse(source.subscribed)
    }
    @Test fun disabledLocationDoesNotReuseCache() = runTest {
        val source = Fake().apply { enabled = false; cached = listOf(sample()) }
        assertEquals(failure(LocationProblem.LOCATION_DISABLED), repo(source).currentPosition())
    }
    @Test fun noProviderGivesExplicitError() = runTest {
        val source = Fake().apply { available = false }
        assertEquals(failure(LocationProblem.NO_PROVIDER), repo(source).currentPosition())
    }
    @Test fun recentCacheAvoidsSensorSubscription() = runTest {
        val source = Fake().apply { cached = listOf(sample(5000)) }
        val result = repo(source).currentPosition() as LocationResult.Success
        assertTrue(result.position.fromCache)
        assertEquals(-33.0245, result.position.latitude, 0.00001)
        assertFalse(source.subscribed)
    }
    @Test fun mostRecentValidCachedFixWins() = runTest {
        val source = Fake().apply { cached = listOf(sample(20000), sample(1000).copy(latitude = -32.0)) }
        assertEquals(-32.0, (repo(source).currentPosition() as LocationResult.Success).position.latitude, 0.00001)
    }
    @Test fun staleCacheRequiresNewFix() = runTest {
        val source = Fake().apply { cached = listOf(sample(60000)); stream = flowOf(sample()) }
        assertFalse((repo(source).currentPosition() as LocationResult.Success).position.fromCache)
        assertTrue(source.subscribed)
    }
    @Test fun approximatePermissionIsAccepted() = runTest {
        val source = Fake().apply { permission = LocationAccess.APPROXIMATE; stream = flowOf(sample().copy(accuracyMeters = 2000f)) }
        val result = repo(source).currentPosition() as LocationResult.Success
        assertEquals(LocationAccess.APPROXIMATE, result.position.access)
        assertEquals(2000f, result.position.accuracyMeters)
    }
    @Test fun impossibleCoordinatesAreIgnored() = runTest {
        val source = Fake().apply { stream = flowOf(sample().copy(latitude = 91.0), sample().copy(longitude = Double.NaN), sample()) }
        assertTrue(repo(source).currentPosition() is LocationResult.Success)
    }
    @Test fun invalidAccuracyIsIgnored() = runTest {
        val source = Fake().apply { stream = flowOf(sample().copy(accuracyMeters = -1f), sample().copy(accuracyMeters = Float.NaN), sample()) }
        assertEquals(15f, (repo(source).currentPosition() as LocationResult.Success).position.accuracyMeters)
    }
    @Test fun futureTimestampIsNotAcceptedAsFreshCache() = runTest {
        val source = Fake().apply { cached = listOf(sample(-1000)); stream = flowOf(sample()) }
        assertFalse((repo(source).currentPosition() as LocationResult.Success).position.fromCache)
    }
    @Test fun waitingForGpsHasATimeLimit() = runTest {
        assertEquals(failure(LocationProblem.TIMEOUT), repo(Fake()).currentPosition())
    }
    @Test fun revokingPermissionDuringRequestIsHandled() = runTest {
        val source = Fake().apply { stream = flow { throw SecurityException() } }
        assertEquals(failure(LocationProblem.PERMISSION_REQUIRED), repo(source).currentPosition())
    }
    @Test fun switchingLocationOffDuringRequestIsHandled() = runTest {
        val source = Fake().apply { stream = flow { throw LocationTurnedOff() } }
        assertEquals(failure(LocationProblem.LOCATION_DISABLED), repo(source).currentPosition())
    }
    @Test fun cancellationReleasesTheLocationSubscription() = runTest {
        var released = false
        val entered = CompletableDeferred<Unit>()
        val source = Fake().apply { stream = flow {
            try { entered.complete(Unit); awaitCancellation() } finally { released = true }
        } }
        val job = launch { repo(source).currentPosition() }
        entered.await()
        job.cancelAndJoin()
        assertTrue(released)
    }
    @Test fun acceptingAFixReleasesTheLocationSubscription() = runTest {
        var released = false
        val source = Fake().apply { stream = flow {
            try { emit(sample()); awaitCancellation() } finally { released = true }
        } }
        assertTrue(repo(source).currentPosition() is LocationResult.Success)
        assertTrue(released)
    }
    @Test fun providerFailureDoesNotCrashTheApp() = runTest {
        val source = Fake().apply { stream = flow { error("Proveedor no disponible") } }
        assertEquals(failure(LocationProblem.UNAVAILABLE), repo(source).currentPosition())
    }
}
