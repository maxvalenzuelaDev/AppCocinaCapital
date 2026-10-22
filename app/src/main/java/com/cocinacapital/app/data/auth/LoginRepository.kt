package com.cocinacapital.app.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LoginUser(val id: String, val email: String?)

sealed interface LoginSession {
    data object Initializing : LoginSession
    data object SignedOut : LoginSession
    data object TemporarilyUnavailable : LoginSession
    data class SignedIn(val user: LoginUser) : LoginSession
}

enum class LoginError(val message: String) {
    INVALID_EMAIL("Ingresa un correo válido."),
    EMPTY_PASSWORD("Ingresa tu contraseña."),
    INVALID_CREDENTIALS("Correo o contraseña incorrectos."),
    EMAIL_NOT_CONFIRMED("Confirma tu correo antes de iniciar sesión."),
    TOO_MANY_ATTEMPTS("Demasiados intentos. Inténtalo más tarde."),
    NETWORK("No se pudo conectar. Revisa tu conexión e inténtalo nuevamente."),
    UNKNOWN("No se pudo completar la operación. Inténtalo nuevamente.")
}

sealed interface LoginResult<out T> {
    data class Success<T>(val value: T) : LoginResult<T>
    data class Failure(val error: LoginError) : LoginResult<Nothing>
}

/** Contrato independiente de la interfaz; nunca expone contraseñas ni tokens. */
interface AuthGateway {
    val sessions: Flow<LoginSession>
    suspend fun signIn(email: String, password: String): LoginUser
    suspend fun restoreSession(): LoginSession
    suspend fun signOut()
}

class LoginRepository(private val gateway: AuthGateway) {
    val sessions: Flow<LoginSession> = gateway.sessions
    private val mutex = Mutex()

    suspend fun login(email: String, password: String): LoginResult<LoginUser> {
        val normalized = email.trim()
        if (!EMAIL.matches(normalized)) return LoginResult.Failure(LoginError.INVALID_EMAIL)
        // No se aplica la política del registro: una cuenta existente puede tener otra longitud.
        // La contraseña se envía intacta, incluidos sus espacios.
        if (password.isEmpty()) return LoginResult.Failure(LoginError.EMPTY_PASSWORD)
        return execute { gateway.signIn(normalized, password) }
    }

    suspend fun restoreSession(): LoginResult<LoginSession> = execute { gateway.restoreSession() }

    suspend fun logout(): LoginResult<Unit> = execute { gateway.signOut() }

    private suspend fun <T> execute(block: suspend () -> T): LoginResult<T> = mutex.withLock {
        try {
            LoginResult.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AuthFailure) {
            LoginResult.Failure(failure.reason)
        } catch (_: Exception) {
            LoginResult.Failure(LoginError.UNKNOWN)
        }
    }

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/** Error seguro: no incluye el cuerpo HTTP ni datos de la sesión. */
class AuthFailure(val reason: LoginError) : Exception(reason.message)
