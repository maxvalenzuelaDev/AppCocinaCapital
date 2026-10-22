package com.cocinacapital.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cocinacapital.app.ui.auth.AppEntry
import com.cocinacapital.app.ui.theme.CocinaCapitalTheme

/**
 * Login y home comparten el cliente y la sesión de Supabase.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CocinaCapitalTheme {
                AppEntry()
            }
        }
    }
}
