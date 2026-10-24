package com.cocinacapital.app.ui.navigation

import androidx.compose.foundation.layout.Box
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cocinacapital.app.ui.detalle.DetalleLocalScreen
import com.cocinacapital.app.ui.detalle.DetallePromocionScreen
import com.cocinacapital.app.ui.home.HomeScreen
import com.cocinacapital.app.ui.perfil.PerfilScreen

/**
 * Estructura principal de la app (ya autenticado): barra inferior + contenido.
 * La barra inferior solo se muestra en las pestañas; las pantallas de detalle la ocultan.
 *
 * Los insets de sistema se desactivan en el Scaffold (`WindowInsets(0, 0, 0, 0)`) porque
 * cada pantalla maneja los suyos: el TopAppBar el de arriba y la NavigationBar el de abajo.
 *
 * El cierre de sesión vive en Perfil: al cerrar, el AuthViewModel detecta la sesión terminada
 * y la app vuelve sola al Login.
 */
@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rutaActual = backStackEntry?.destination?.route
    val esPestana = rutaActual == null || BottomNavItem.items.any { it.route == rutaActual }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (esPestana) NavigationBar {
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
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onLocalClick = { id -> navController.navigate(RutasDetalle.local(id)) },
                    onPromocionClick = { id -> navController.navigate(RutasDetalle.promocion(id)) }
                )
            }
            composable(BottomNavItem.Explorar.route) { PantallaPendiente("Explorar (locales y mapa)") }
            composable(BottomNavItem.Billetera.route) { PantallaPendiente("Billetera (mis beneficios)") }
            composable(BottomNavItem.Perfil.route) { PerfilScreen() }

            composable(
                route = RutasDetalle.LOCAL,
                arguments = listOf(navArgument(RutasDetalle.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                DetalleLocalScreen(
                    localId = entry.arguments?.getString(RutasDetalle.ARG_ID).orEmpty(),
                    onVolver = { navController.navigateUp() }
                )
            }
            composable(
                route = RutasDetalle.PROMOCION,
                arguments = listOf(navArgument(RutasDetalle.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                DetallePromocionScreen(
                    promocionId = entry.arguments?.getString(RutasDetalle.ARG_ID).orEmpty(),
                    onVolver = { navController.navigateUp() }
                )
            }
        }
    }
}

/** Rutas con argumento que no pertenecen a la barra inferior. */
private object RutasDetalle {
    const val ARG_ID = "id"
    const val LOCAL = "local/{$ARG_ID}"
    const val PROMOCION = "promocion/{$ARG_ID}"

    fun local(id: String) = "local/$id"
    fun promocion(id: String) = "promocion/$id"
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