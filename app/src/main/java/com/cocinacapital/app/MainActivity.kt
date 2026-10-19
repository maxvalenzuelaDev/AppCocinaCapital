package com.cocinacapital.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cocinacapital.app.ui.navigation.MainScaffold
import com.cocinacapital.app.ui.theme.CocinaCapitalTheme

/**
 * Para probar el Home de forma aislada. Cuando Login/Registro estén listos, el flujo
 * debería ser: Login → (autenticado) → MainScaffold().
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CocinaCapitalTheme {
                MainScaffold()
            }
        }
    }
}
