package com.cocinacapital.app.ui.auth

import com.cocinacapital.app.data.auth.AuthGateway
import com.cocinacapital.app.data.auth.LoginRepository
import com.cocinacapital.app.data.auth.LoginSession
import com.cocinacapital.app.data.auth.LoginUser
import com.cocinacapital.app.data.auth.Rol
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
    private class FakeGateway(val rol: Rol = Rol.CONSUMIDOR) : AuthGateway {
        override val sessions = MutableStateFlow<LoginSession>(LoginSession.SignedOut)
        override suspend fun signIn(email: String, password: String) =
            LoginUser("user-1", email, rol).also { sessions.value = LoginSession.SignedIn(it) }
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
    @Test fun `una cuenta comun inicia sesion como consumidor`() {
        val model = AuthViewModel(LoginRepository(gateway), FakeHistory())
        model.login("a@b.cl", "abc")
        assertEquals(Rol.CONSUMIDOR, model.uiState.value.user?.rol)
    }
    @Test fun `el login distingue al oferente y lo conserva al recuperar la sesion`() {
        val oferente = FakeGateway(Rol.OFERENTE)
        val model = AuthViewModel(LoginRepository(oferente), FakeHistory())
        model.login("local@b.cl", "abc")
        assertEquals(AuthScreen.SESSION, model.uiState.value.screen)
        assertEquals(Rol.OFERENTE, model.uiState.value.user?.rol)
        model.logout()
        assertNull(model.uiState.value.user)
        oferente.sessions.value = LoginSession.SignedIn(LoginUser("user-1", "local@b.cl", Rol.OFERENTE))
        assertEquals(Rol.OFERENTE, model.uiState.value.user?.rol)
    }
}
