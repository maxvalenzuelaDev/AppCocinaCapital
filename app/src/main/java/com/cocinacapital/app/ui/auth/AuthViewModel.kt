package com.cocinacapital.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocinacapital.app.data.auth.*
import com.cocinacapital.app.data.local.HistorialSesion
import com.cocinacapital.app.di.AppContainer
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

enum class AuthScreen { LOGIN, SESSION }
data class AuthUiState(
    val screen: AuthScreen = AuthScreen.LOGIN,
    val restoring: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val user: LoginUser? = null,
    /** Nunca hubo sesión en este teléfono: el Login da la bienvenida en vez de "volver a verte". */
    val firstVisit: Boolean = false
)

class AuthViewModel(
    private val repository: LoginRepository,
    private val history: HistorialSesion
) : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer { AuthViewModel(AppContainer.authRepository, AppContainer.historialSesion) }
        }
    }
    private val state = MutableStateFlow(AuthUiState(firstVisit = !history.huboSesion()))
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessions.collect { session ->
                when (session) {
                    is LoginSession.SignedIn -> {
                        // Login, registro o sesión recuperada: desde ahora el Login dice "volver a verte".
                        history.registrarSesion()
                        state.update {
                            it.copy(screen = AuthScreen.SESSION, restoring = false, user = session.user, error = null, firstVisit = false)
                        }
                    }
                    LoginSession.SignedOut -> state.update {
                        it.copy(restoring = false, user = null, screen = if (it.screen == AuthScreen.SESSION) AuthScreen.LOGIN else it.screen)
                    }
                    LoginSession.Initializing -> Unit
                    LoginSession.TemporarilyUnavailable -> state.update {
                        it.copy(restoring = false, screen = AuthScreen.LOGIN, user = null,
                            error = "No pudimos recuperar tu sesión. Revisa tu conexión e inicia sesión nuevamente.")
                    }
                }
            }
        }
    }

    fun dismissError() { state.update { it.copy(error = null) } }

    fun login(email: String, password: String) = perform {
        when (val result = repository.login(email, password)) {
            is LoginResult.Failure -> state.update { it.copy(error = result.error.message) }
            is LoginResult.Success -> state.update { it.copy(screen = AuthScreen.SESSION, user = result.value) }
        }
    }

    fun logout() = perform {
        when (val result = repository.logout()) {
            is LoginResult.Failure -> state.update { it.copy(error = result.error.message) }
            is LoginResult.Success -> state.update { it.copy(screen = AuthScreen.LOGIN, user = null, notice = "Cerraste tu sesión.") }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (state.value.busy || state.value.restoring) return
        state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try { withTimeout(30000) { action() } }
            catch (_: TimeoutCancellationException) { state.update { it.copy(error = "La conexión tardó demasiado. Inténtalo nuevamente.") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(error = "No se pudo conectar. Revisa tu conexión e inténtalo nuevamente.") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
}
