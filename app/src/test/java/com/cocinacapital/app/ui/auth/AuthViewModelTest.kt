package com.cocinacapital.app.ui.auth

import com.cocinacapital.app.data.auth.AuthGateway
import com.cocinacapital.app.data.auth.LoginRepository
import com.cocinacapital.app.data.auth.LoginSession
import com.cocinacapital.app.data.auth.LoginUser
import com.cocinacapital.app.data.local.HistorialSesion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private class FakeGateway : AuthGateway {
        override val sessions = MutableStateFlow<LoginSession>(LoginSession.SignedOut)
        override suspend fun signIn(email: String, password: String) =
            LoginUser("user-1", email).also { sessions.value = LoginSession.SignedIn(it) }
        override suspend fun restoreSession() = sessions.value
        override suspend fun signOut() { sessions.value = LoginSession.SignedOut }
    }

    private class FakeHistory(var hubo: Boolean = false) : HistorialSesion {
        override fun huboSesion() = hubo
        override fun registrarSesion() { hubo = true }
    }

    private val gateway = FakeGateway()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun `primer uso da la bienvenida`() {
        assertTrue(AuthViewModel(LoginRepository(gateway), FakeHistory(hubo = false)).uiState.value.firstVisit)
    }
    @Test fun `si ya hubo sesion dice volver a verte`() {
        assertFalse(AuthViewModel(LoginRepository(gateway), FakeHistory(hubo = true)).uiState.value.firstVisit)
    }
    @Test fun `al iniciar sesion se recuerda y al salir ya no es primer uso`() {
        val history = FakeHistory(hubo = false)
        val model = AuthViewModel(LoginRepository(gateway), history)
        model.login("a@b.cl", "abc")
        assertTrue(history.hubo)
        gateway.sessions.value = LoginSession.SignedOut
        assertEquals(AuthScreen.LOGIN, model.uiState.value.screen)
        assertFalse(model.uiState.value.firstVisit)
    }
}
