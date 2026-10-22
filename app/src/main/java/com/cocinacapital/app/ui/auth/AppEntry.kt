package com.cocinacapital.app.ui.auth

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.widget.Toast
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
import com.cocinacapital.app.ui.navigation.MainScaffold

const val REGISTER_ACTION = "com.cocinacapital.action.OPEN_REGISTER"

/** Conecta el login con el home existente, observando la sesión del cliente común. */
@Composable
fun AppEntry(onRegisterRequested: (() -> Unit)? = null) {
    if (!SupabaseProvider.isConfigured) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("El servicio de acceso todavía no está disponible.")
        }
        return
    }
    val context = LocalContext.current
    val model: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val lightBars = !isSystemInDarkTheme()
    SideEffect {
        context.hostActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }
    val openRegister: () -> Unit = onRegisterRequested ?: {
        val intent = Intent(REGISTER_ACTION).setPackage(context.packageName)
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else Toast.makeText(context, "El registro todavía no está disponible en esta versión.", Toast.LENGTH_LONG).show()
    }
    when (state.screen) {
        AuthScreen.LOGIN -> LoginScreen(onRegisterClick = openRegister, model = model)
        AuthScreen.SESSION -> MainScaffold()
    }
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.hostActivity()
    else -> null
}
