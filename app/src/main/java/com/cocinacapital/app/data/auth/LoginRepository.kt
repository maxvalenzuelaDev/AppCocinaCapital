package com.cocinacapital.app.data.auth

import androidx.annotation.StringRes
import com.cocinacapital.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rol de la cuenta (HU-04). Toda cuenta es consumidora salvo que Supabase la marque como oferente. */
enum class Rol { CONSUMIDOR, OFERENTE }

data class LoginUser(val id: String, val email: String?, val rol: Rol = Rol.CONSUMIDOR)

sealed interface LoginSession {
    data object Initializing : LoginSession
    data object SignedOut : LoginSession
    data object TemporarilyUnavailable : LoginSession
    data class SignedIn(val user: LoginUser) : LoginSession
}

/** Cada error trae su texto en `res/values/acceso.xml` (T-10). */
enum class LoginError(@StringRes val mensajeRes: Int) {
    INVALID_EMAIL(R.string.login_error_correo_invalido),
    EMPTY_PASSWORD(R.string.login_error_contrasena_vacia),
    INVALID_CREDENTIALS(R.string.login_error_credenciales),
    EMAIL_NOT_CONFIRMED(R.string.login_error_correo_sin_confirmar),
    TOO_MANY_ATTEMPTS(R.string.login_error_demasiados_intentos),
    NETWORK(R.string.login_error_sin_conexion),
    UNKNOWN(R.string.login_error_desconocido)
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
class AuthFailure(val reason: LoginError) : Exception(reason.name)
