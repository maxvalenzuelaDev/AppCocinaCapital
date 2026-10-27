package com.cocinacapital.app.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/** Recibe el cliente compartido de la aplicación, con Auth instalado. */
class SupabaseAuthGateway(client: SupabaseClient) : AuthGateway {
    private val auth = client.auth

    override val sessions: Flow<LoginSession> = auth.sessionStatus
        .map { it.toLoginSession() }
        .distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): LoginUser = request {
        auth.awaitInitialization()
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        auth.currentUserOrNull()?.toLoginUser()
            ?: throw AuthFailure(LoginError.UNKNOWN)
    }

    override suspend fun restoreSession(): LoginSession = request {
        // El SDK carga la sesión guardada y gestiona la renovación del token.
        auth.awaitInitialization()
        auth.sessionStatus.value.toLoginSession()
    }

    override suspend fun signOut(): Unit = request {
        auth.awaitInitialization()
        auth.signOut()
    }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: AuthFailure) {
        throw failure
    } catch (error: AuthRestException) {
        throw AuthFailure(when (error.error) {
            "invalid_credentials" -> LoginError.INVALID_CREDENTIALS
            "email_not_confirmed" -> LoginError.EMAIL_NOT_CONFIRMED
            "over_request_rate_limit", "over_email_send_rate_limit" -> LoginError.TOO_MANY_ATTEMPTS
            else -> LoginError.UNKNOWN
        })
    } catch (_: HttpRequestTimeoutException) {
        throw AuthFailure(LoginError.NETWORK)
    } catch (_: HttpRequestException) {
        throw AuthFailure(LoginError.NETWORK)
    } catch (_: IOException) {
        throw AuthFailure(LoginError.NETWORK)
    }
}

private fun UserInfo.toLoginUser() = LoginUser(id, email, rolDesde(appMetadata))

/**
 * El rol se lee de `app_metadata`, que solo se puede modificar desde Supabase (no desde la app),
 * así un usuario no puede convertirse en oferente por su cuenta. Su origen es la tabla `perfiles`
 * (T-04), que lo copia ahí con un trigger. Ver supabase/asignar_oferente.sql.
 */
internal fun rolDesde(appMetadata: JsonObject?): Rol =
    when ((appMetadata?.get("rol") as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()) {
        "oferente" -> Rol.OFERENTE
        else -> Rol.CONSUMIDOR
    }

private fun SessionStatus.toLoginSession(): LoginSession = when (this) {
    SessionStatus.Initializing -> LoginSession.Initializing
    is SessionStatus.NotAuthenticated -> LoginSession.SignedOut
    is SessionStatus.RefreshFailure -> LoginSession.TemporarilyUnavailable
    is SessionStatus.Authenticated -> session.user?.let { LoginSession.SignedIn(it.toLoginUser()) }
        ?: LoginSession.TemporarilyUnavailable
}
