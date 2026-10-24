package com.cocinacapital.app.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LoginRepositoryTest {
    private class FakeGateway : AuthGateway {
        override val sessions = MutableStateFlow<LoginSession>(LoginSession.SignedOut)
        var received: Pair<String, String>? = null
        var failure: Exception? = null
        override suspend fun signIn(email: String, password: String): LoginUser {
            failure?.let { throw it }
            received = email to password
            return LoginUser("user-1", email).also { sessions.value = LoginSession.SignedIn(it) }
        }
        override suspend fun restoreSession() = sessions.value
        override suspend fun signOut() { failure?.let { throw it }; sessions.value = LoginSession.SignedOut }
    }

    @Test fun `rechaza correo invalido sin llamar al servidor`() = runBlocking {
        val gateway = FakeGateway()
        assertEquals(LoginResult.Failure(LoginError.INVALID_EMAIL), LoginRepository(gateway).login("no-es-correo", "abc"))
        assertNull(gateway.received)
    }
    @Test fun `rechaza contraseña vacia`() = runBlocking {
        assertEquals(LoginResult.Failure(LoginError.EMPTY_PASSWORD), LoginRepository(FakeGateway()).login("a@b.cl", ""))
    }
    @Test fun `recorta correo y conserva contraseña exacta`() = runBlocking {
        val gateway = FakeGateway()
        assertIs<LoginResult.Success<LoginUser>>(LoginRepository(gateway).login("  a@b.cl  ", " x "))
        assertEquals("a@b.cl" to " x ", gateway.received)
    }
    @Test fun `propaga error seguro de credenciales`() = runBlocking {
        val gateway = FakeGateway().apply { failure = AuthFailure(LoginError.INVALID_CREDENTIALS) }
        assertEquals(LoginResult.Failure(LoginError.INVALID_CREDENTIALS), LoginRepository(gateway).login("a@b.cl", "bad"))
    }
    @Test fun `no expone mensaje interno`() = runBlocking {
        val gateway = FakeGateway().apply { failure = IllegalStateException("dato privado") }
        assertEquals(LoginResult.Failure(LoginError.UNKNOWN), LoginRepository(gateway).login("a@b.cl", "abc"))
    }
    @Test fun `respeta cancelacion de corutina`(): Unit = runBlocking {
        val gateway = FakeGateway().apply { failure = CancellationException() }
        assertFailsWith<CancellationException> { LoginRepository(gateway).login("a@b.cl", "abc") }
    }
    @Test fun `restaura y cierra sesion`() = runBlocking {
        val gateway = FakeGateway()
        val repository = LoginRepository(gateway)
        repository.login("a@b.cl", "abc")
        assertEquals(LoginResult.Success(LoginSession.SignedIn(LoginUser("user-1", "a@b.cl"))), repository.restoreSession())
        assertEquals(LoginResult.Success(Unit), repository.logout())
        assertEquals(LoginResult.Success(LoginSession.SignedOut), repository.restoreSession())
    }
    @Test fun `logout fallido no simula sesion cerrada`(): Unit = runBlocking {
        val gateway = FakeGateway()
        val repository = LoginRepository(gateway)
        repository.login("a@b.cl", "abc")
        gateway.failure = AuthFailure(LoginError.NETWORK)
        assertEquals(LoginResult.Failure(LoginError.NETWORK), repository.logout())
        assertIs<LoginSession.SignedIn>(gateway.sessions.value)
    }
}
