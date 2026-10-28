package com.cocinacapital.app.ui.navigation

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.cocinacapital.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.ui.components.AvisoSinConexion
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.cocinacapital.app.ui.location.rememberUbicacionAction
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
    val snackbar = remember { SnackbarHostState() }
    val ubicacion = rememberUbicacionAction(snackbar)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rutaActual = backStackEntry?.destination?.route
    var puenteBusqueda by rememberSaveable { mutableStateOf(false) }
    var restaurandoHome by rememberSaveable { mutableStateOf(false) }
    var categoriaBusqueda by rememberSaveable { mutableStateOf<String?>(null) }
    fun cerrarPuenteBusqueda() {
        runCatching { navController.getBackStackEntry(BottomNavItem.Explorar.route) }.getOrNull()
            ?.savedStateHandle?.set(BUSQUEDA_ANCLADA, false)
        puenteBusqueda = false
    }
    fun volverAHome() {
        cerrarPuenteBusqueda(); restaurandoHome = true
        if (rutaActual != BottomNavItem.Home.route) navController.navegarA(BottomNavItem.Home)
    }
    BackHandler(puenteBusqueda && rutaActual == BottomNavItem.Home.route, onBack = ::volverAHome)
    // HU-22: una notificación de promoción abre su detalle.
    val promocionPendiente by AppContainer.promocionPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(promocionPendiente) {
        val id = promocionPendiente ?: return@LaunchedEffect
        navController.navigate(RutasDetalle.promocion(id)) { launchSingleTop = true }
        AppContainer.promocionAbierta()
    }
    val esPestana = rutaActual == null || BottomNavItem.items.any { it.route == rutaActual }
    val buscandoEnExplorar = rutaActual == BottomNavItem.Explorar.route && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val mostrandoPuente = (puenteBusqueda || restaurandoHome) && (rutaActual == BottomNavItem.Home.route || rutaActual == BottomNavItem.Explorar.route)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val conBarra = esPestana && !buscandoEnExplorar && !mostrandoPuente
            Column {
                // HU-35: sobre la barra inferior; sin barra (detalles), por encima de la navegación del sistema.
                AvisoSinConexion(if (conBarra) Modifier else Modifier.navigationBarsPadding())
                // expand/shrink (no slide) para que el contenido también se ajuste suavemente.
                AnimatedVisibility(
                    visible = conBarra,
                    enter = expandVertically(tween(DURACION_MS)),
                    exit = shrinkVertically(tween(DURACION_MS))
                ) { NavigationBar {
                    BottomNavItem.items.forEach { item ->
                        val seleccionado = rutaActual == item.route
                        NavigationBarItem(
                            selected = seleccionado,
                            onClick = {
                                if (item == BottomNavItem.Home && puenteBusqueda) volverAHome()
                                else {
                                    if (item != BottomNavItem.Explorar) cerrarPuenteBusqueda()
                                    navController.navegarA(item)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (seleccionado) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                } }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding),
            // Hacia un detalle: desliza. Entre pestañas: fundido.
            enterTransition = {
                if ((puenteBusqueda || restaurandoHome) && enlaceBusqueda(initialState.destination.route, targetState.destination.route)) EnterTransition.None
                else if (targetState.destination.route.esDetalle()) slideIntoContainer(SlideDirection.Start, tween(DURACION_MS))
                else fadeIn(tween(DURACION_MS))
            },
            exitTransition = {
                if ((puenteBusqueda || restaurandoHome) && enlaceBusqueda(initialState.destination.route, targetState.destination.route)) ExitTransition.None
                else if (targetState.destination.route.esDetalle())
                    slideOutOfContainer(SlideDirection.Start, tween(DURACION_MS)) { it / 4 } + fadeOut(tween(DURACION_MS))
                else fadeOut(tween(DURACION_MS))
            },
            // Al volver desde un detalle: el movimiento inverso.
            popEnterTransition = {
                if ((puenteBusqueda || restaurandoHome) && enlaceBusqueda(initialState.destination.route, targetState.destination.route)) EnterTransition.None
                else if (initialState.destination.route.esDetalle())
                    slideIntoContainer(SlideDirection.End, tween(DURACION_MS)) { it / 4 } + fadeIn(tween(DURACION_MS))
                else fadeIn(tween(DURACION_MS))
            },
            popExitTransition = {
                if ((puenteBusqueda || restaurandoHome) && enlaceBusqueda(initialState.destination.route, targetState.destination.route)) ExitTransition.None
                else if (initialState.destination.route.esDetalle()) slideOutOfContainer(SlideDirection.End, tween(DURACION_MS))
                else fadeOut(tween(DURACION_MS))
            }
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onLocationClick = ubicacion.solicitar,
                    obteniendoUbicacion = ubicacion.cargando,
                    onLocalClick = { id -> navController.navigate(RutasDetalle.local(id)) },
                    onPromocionClick = { id -> navController.navigate(RutasDetalle.promocion(id)) },
                    onSearchClick = { categoria ->
                        categoriaBusqueda = categoria; puenteBusqueda = true
                    },
                    elevandoBusqueda = puenteBusqueda,
                    restaurandoBusqueda = restaurandoHome,
                    onBusquedaRestaurada = { restaurandoHome = false },
                    onBusquedaElevada = {
                        navController.navegarA(BottomNavItem.Explorar)
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(CATEGORIA_DESDE_HOME, categoriaBusqueda)
                            set(BUSQUEDA_ANCLADA, true)
                            set(BUSQUEDA_DESDE_HOME, true)
                        }
                    }
                )
            }
            composable(BottomNavItem.Explorar.route) { entry ->
                val solicitarBusqueda by entry.savedStateHandle.getStateFlow(BUSQUEDA_DESDE_HOME, false).collectAsStateWithLifecycle()
                val barraAnclada by entry.savedStateHandle.getStateFlow(BUSQUEDA_ANCLADA, false).collectAsStateWithLifecycle()
                com.cocinacapital.app.ui.explore.ExploreScreen(
                    onExperiencias = { navController.navigate(RutasDetalle.EXPERIENCIAS) },
                    onLocation = ubicacion.solicitar,
                    ubicacionEnCurso = ubicacion.cargando,
                    snackbar = snackbar,
                    onLocalDetail = { id -> navController.navigate(RutasDetalle.local(id)) },
                    onPreferences = { cerrarPuenteBusqueda(); navController.navegarA(BottomNavItem.Perfil) },
                    solicitarBusqueda = solicitarBusqueda,
                    barraAnclada = barraAnclada,
                    onCerrarBusqueda = ::cerrarPuenteBusqueda,
                    categoriaDesdeHome = entry.savedStateHandle[CATEGORIA_DESDE_HOME],
                    onBusquedaConsumida = {
                        entry.savedStateHandle[BUSQUEDA_DESDE_HOME] = false
                        entry.savedStateHandle.remove<String>(CATEGORIA_DESDE_HOME)
                    }
                )
            }
            composable(RutasDetalle.EXPERIENCIAS) {
                com.cocinacapital.app.ui.experiencias.ExperienciasScreen(
                    onVolver = { navController.navigateUp() },
                    onDetalle = { id -> navController.navigate(RutasDetalle.experiencia(id)) }
                )
            }
            composable(RutasDetalle.EXPERIENCIA,
                arguments = listOf(navArgument(RutasDetalle.ARG_ID) { type = NavType.StringType })) { entry ->
                com.cocinacapital.app.ui.experiencias.DetalleExperienciaScreen(
                    id = entry.arguments?.getString(RutasDetalle.ARG_ID).orEmpty(),
                    onVolver = { navController.navigateUp() },
                    onLocal = { id -> navController.navigate(RutasDetalle.local(id)) { launchSingleTop = true } }
                )
            }

            composable(BottomNavItem.Billetera.route) {
                BilleteraScreen(
                    onExplorarPromociones = {
                        navController.navegarA(BottomNavItem.Home)
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
                    onVolver = { navController.navigateUp() },
                    onLocalClick = { id ->
                        val anterior = navController.previousBackStackEntry
                        if (anterior?.destination?.route == RutasDetalle.LOCAL &&
                            anterior.arguments?.getString(RutasDetalle.ARG_ID) == id) {
                            navController.navigateUp()
                        } else {
                            navController.navigate(RutasDetalle.local(id)) { launchSingleTop = true }
                        }
                    }
                )
            }
        }
    }
}

private const val DURACION_MS = 300
private const val BUSQUEDA_DESDE_HOME = "home_abrir_busqueda"
private const val CATEGORIA_DESDE_HOME = "home_categoria_busqueda"
private const val BUSQUEDA_ANCLADA = "home_busqueda_anclada"

private fun enlaceBusqueda(desde: String?, hasta: String?) =
    (desde == BottomNavItem.Home.route && hasta == BottomNavItem.Explorar.route) ||
        (desde == BottomNavItem.Explorar.route && hasta == BottomNavItem.Home.route)

private fun String?.esDetalle() = this == RutasDetalle.LOCAL || this == RutasDetalle.PROMOCION ||
    this == RutasDetalle.EXPERIENCIAS || this == RutasDetalle.EXPERIENCIA

/** Rutas con argumento que no pertenecen a la barra inferior. */
private object RutasDetalle {
    const val EXPERIENCIAS = "experiencias"
    const val EXPERIENCIA = "experiencia/{id}"
    fun experiencia(id: String) = "experiencia/$id"
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
        Text(stringResource(R.string.nav_proximamente, titulo))
    }
}
