package com.cocinacapital.app

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import com.cocinacapital.app.ui.inicio.InicioViewModel
import com.cocinacapital.app.ui.inicio.InicioUiState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocinacapital.app.data.model.TemaApp
import com.cocinacapital.app.data.remote.SupabaseProvider
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.ui.auth.AppEntry
import com.cocinacapital.app.ui.theme.CocinaCapitalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Login y Home comparten el cliente y la sesión de Supabase.
 * El tema elegido en Perfil se guarda en Room y se aplica a toda la app.
 */
class MainActivity : ComponentActivity() {
    private val inicio: InicioViewModel by viewModels { InicioViewModel.Factory }
    private var serviciosListos = false
    override fun onResume() {
        super.onResume()
        if (serviciosListos) AppContainer.ubicacionRepository.validarDisponibilidad()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Muestra el splash de marca (T-09) y luego aplica el tema normal; debe ir antes de super.onCreate.
        // Se mantiene mientras se prepara el inicio, así no aparece una pantalla de carga con un tema
        // distinto al elegido en Perfil (ese tema se lee de Room recién cuando termina la preparación).
        installSplashScreen().setKeepOnScreenCondition { inicio.uiState.value == InicioUiState.Loading }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val estado by inicio.uiState.collectAsStateWithLifecycle()
            if (estado != InicioUiState.Success) {
                CocinaCapitalTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            if (estado == InicioUiState.Loading) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.inicio_preparando))
                            } else {
                                Text(stringResource(R.string.inicio_error))
                                Button(onClick = inicio::reintentar) { Text(stringResource(R.string.detalle_reintentar)) }
                            }
                        }
                    }
                }
                return@setContent
            }
            LaunchedEffect(Unit) {
                serviciosListos = true
                AppContainer.observarSesion()
                AppContainer.ubicacionRepository.validarDisponibilidad()
            }
            // Sin credenciales de Supabase no se puede crear la sesión; se usa el tema del sistema.
            val temaFlow: Flow<TemaApp> = if (SupabaseProvider.isConfigured) {
                AppContainer.preferenciasRepository.observar().map { it.tema }
            } else {
                flowOf(TemaApp.SISTEMA)
            }
            val tema by temaFlow.collectAsStateWithLifecycle(initialValue = TemaApp.SISTEMA)

            CocinaCapitalTheme(temaApp = tema) {
                AppEntry()
            }
        }
    }
}