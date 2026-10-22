package com.cocinacapital.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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