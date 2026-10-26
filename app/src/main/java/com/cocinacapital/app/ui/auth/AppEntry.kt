package com.cocinacapital.app.ui.auth

import androidx.compose.ui.res.stringResource
import com.cocinacapital.app.R
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.data.remote.SupabaseProvider
import com.cocinacapital.app.data.auth.Rol
import com.cocinacapital.app.ui.navigation.MainScaffold
import com.cocinacapital.app.ui.oferente.OferenteScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.cocinacapital.app.ui.registro.RegistroScreen
import com.cocinacapital.app.ui.registro.RegistroViewModel

const val REGISTER_ACTION = "com.cocinacapital.action.OPEN_REGISTER"

/** Conecta el login con el home existente, observando la sesión del cliente común. */
@Composable
fun AppEntry(onRegisterRequested: (() -> Unit)? = null) {
    if (!SupabaseProvider.isConfigured) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.acceso_sin_configuracion))
        }
        return
    }
    val context = LocalContext.current
    // Controla si se muestra la pantalla de registro (se conserva al girar la pantalla).
    var mostrarRegistro by rememberSaveable { mutableStateOf(false) }
    val model: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    // Mismo ViewModel que usa RegistroScreen: vive mientras la app esté abierta, por eso se reinicia al salir.
    val registroModel: RegistroViewModel = viewModel()
    val cerrarRegistro = {
        registroModel.reiniciar()
        mostrarRegistro = false
    }
    val view = LocalView.current
    val lightBars = !com.cocinacapital.app.ui.theme.LocalDarkTheme.current
    SideEffect {
        context.hostActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }
    // Al iniciar sesión (también tras registrarse sin confirmar correo) se olvida el registro;
    // así, al cerrar sesión, la app vuelve al Login y no al formulario de registro.
    LaunchedEffect(state.screen) {
        if (state.screen == AuthScreen.SESSION) cerrarRegistro()
    }
    // El botón atrás del teléfono vuelve del registro al Login en vez de cerrar la app.
    BackHandler(enabled = mostrarRegistro && state.screen != AuthScreen.SESSION) {
        cerrarRegistro()
    }
    // Abre el registro cuando se presiona "Crear cuenta".
    val openRegister: () -> Unit = onRegisterRequested ?: {
        mostrarRegistro = true
    }
    // Muestra el login, el registro o la pantalla principal.
    when {
        // HU-04: la navegación y las opciones dependen del rol de la cuenta.
        state.screen == AuthScreen.SESSION -> {
            androidx.compose.runtime.key(state.user?.id) {
                if (state.user?.rol == Rol.OFERENTE) {
                    OferenteScaffold(
                        correo = state.user?.email,
                        cerrandoSesion = state.busy,
                        onCerrarSesion = model::logout
                    )
                } else MainScaffold()
            }
        }

        mostrarRegistro -> {
            RegistroScreen(
                onVolverLogin = cerrarRegistro,
                viewModel = registroModel
            )
        }

        else -> {
            LoginScreen(
                onRegisterClick = openRegister,
                model = model
            )
        }
    }
}
private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.hostActivity()
    else -> null
}
