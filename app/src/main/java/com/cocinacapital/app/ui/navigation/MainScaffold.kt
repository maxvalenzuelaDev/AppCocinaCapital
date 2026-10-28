package com.cocinacapital.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.cocinacapital.app.ui.billetera.BilleteraScreen

/**
 * Estructura principal de la app (ya autenticado): barra inferior + contenido.
 * La barra inferior solo se muestra en las pestañas; las pantallas de detalle la ocultan.
 *
 * Animaciones (T-24): el detalle entra deslizándose desde la derecha y sale hacia la derecha al
 * volver; entre pestañas hay un fundido; la barra inferior se oculta y aparece con una animación.
 * Compose respeta la opción del sistema "Quitar animaciones" (escala de animación en 0).
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
            // expand/shrink (no slide) para que el contenido también se ajuste suavemente.
            AnimatedVisibility(
                visible = esPestana,
                enter = expandVertically(tween(DURACION_MS)),
                exit = shrinkVertically(tween(DURACION_MS))
            ) { NavigationBar {
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
            } }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding),
            // Hacia un detalle: desliza. Entre pestañas: fundido.
            enterTransition = {
                if (targetState.destination.route.esDetalle()) slideIntoContainer(SlideDirection.Start, tween(DURACION_MS))
                else fadeIn(tween(DURACION_MS))
            },
            exitTransition = {
                if (targetState.destination.route.esDetalle())
                    slideOutOfContainer(SlideDirection.Start, tween(DURACION_MS)) { it / 4 } + fadeOut(tween(DURACION_MS))
                else fadeOut(tween(DURACION_MS))
            },
            // Al volver desde un detalle: el movimiento inverso.
            popEnterTransition = {
                if (initialState.destination.route.esDetalle())
                    slideIntoContainer(SlideDirection.End, tween(DURACION_MS)) { it / 4 } + fadeIn(tween(DURACION_MS))
                else fadeIn(tween(DURACION_MS))
            },
            popExitTransition = {
                if (initialState.destination.route.esDetalle()) slideOutOfContainer(SlideDirection.End, tween(DURACION_MS))
                else fadeOut(tween(DURACION_MS))
            }
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onLocalClick = { id -> navController.navigate(RutasDetalle.local(id)) },
                    onPromocionClick = { id -> navController.navigate(RutasDetalle.promocion(id)) }
                )
            }
            composable(BottomNavItem.Explorar.route) { PantallaPendiente("Explorar (locales y mapa)") }
            // Muestra las promociones guardadas del consumidor.
            composable(BottomNavItem.Billetera.route) {
                BilleteraScreen(
                    onExplorarPromociones = {
                        navController.navigate(BottomNavItem.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(BottomNavItem.Perfil.route) { PerfilScreen() }

            composable(
                route = RutasDetalle.LOCAL,
                arguments = listOf(navArgument(RutasDetalle.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                DetalleLocalScreen(
                    localId = entry.arguments?.getString(RutasDetalle.ARG_ID).orEmpty(),
                    onVolver = { navController.navigateUp() },
                    onPromocionClick = { id -> navController.navigate(RutasDetalle.promocion(id)) }
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

private const val DURACION_MS = 300

private fun String?.esDetalle() = this == RutasDetalle.LOCAL || this == RutasDetalle.PROMOCION

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