package com.cocinacapital.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cocinacapital.app.ui.home.HomeScreen

/**
 * Estructura principal de la app (ya autenticado): barra inferior + contenido.
 *
 * Los insets de sistema se desactivan en el Scaffold (`WindowInsets(0, 0, 0, 0)`) porque
 * cada pantalla maneja los suyos: el TopAppBar el de arriba y la NavigationBar el de abajo.
 */
@Composable
fun MainScaffold(userEmail: String? = null, onLogout: () -> Unit = {}, logoutInProgress: Boolean = false, authError: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rutaActual = backStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                BottomNavItem.items.forEach { item ->
                    val seleccionado = rutaActual == item.route
                    NavigationBarItem(
                        selected = seleccionado,
                        onClick = { navController.navegarA(item) },
                        icon = {
                            Icon(
                                imageVector = if (seleccionado) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) { HomeScreen() }
            composable(BottomNavItem.Explorar.route) { PantallaPendiente("Explorar (locales y mapa)") }
            composable(BottomNavItem.Billetera.route) { PantallaPendiente("Billetera (mis beneficios)") }
            composable(BottomNavItem.Perfil.route) {
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                    Text("Mi cuenta")
                    Text(userEmail.orEmpty())
                    authError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                    Button(onClick = onLogout, enabled = !logoutInProgress) {
                        if (logoutInProgress) CircularProgressIndicator() else Text("Cerrar sesión")
                    }
                }
            }
        }
    }
}

/** Navegación entre pestañas: conserva el estado de cada una y evita duplicar pantallas. */
private fun NavHostController.navegarA(item: BottomNavItem) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Marcador de posición para las pestañas que aún no se desarrollan. */
@Composable
private fun PantallaPendiente(titulo: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$titulo — próximamente")
    }
}
