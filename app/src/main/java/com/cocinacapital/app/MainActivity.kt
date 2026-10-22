package com.cocinacapital.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocinacapital.app.data.model.TemaApp
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.ui.navigation.MainScaffold
import com.cocinacapital.app.ui.theme.CocinaCapitalTheme
import kotlinx.coroutines.flow.map

/**
 * Para probar el Home y el Perfil de forma aislada. Cuando Login/Registro estén listos, el flujo
 * debería ser: Login → (autenticado) → MainScaffold().
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // El tema elegido en Perfil se guarda en Room y se aplica a toda la app.
            val tema by AppContainer.preferenciasRepository.observar()
                .map { it.tema }
                .collectAsStateWithLifecycle(initialValue = TemaApp.SISTEMA)
            CocinaCapitalTheme(temaApp = tema) { MainScaffold() }
        }
    }
}
