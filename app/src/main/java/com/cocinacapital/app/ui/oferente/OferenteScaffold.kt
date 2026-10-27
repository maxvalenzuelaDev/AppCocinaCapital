package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R

/** Pestañas del oferente: gestiona su local y sus promociones, no explora como consumidor. */
enum class OferenteTab(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MIS_LOCALES(R.string.oferente_nav_locales, Icons.Filled.Storefront, Icons.Outlined.Storefront),
    CATALOGO(R.string.oferente_nav_catalogo, Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
    PROMOCIONES(R.string.oferente_nav_promociones, Icons.Filled.LocalOffer, Icons.Outlined.LocalOffer),
    CUENTA(R.string.oferente_nav_cuenta, Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * Estructura principal para cuentas con rol oferente (HU-04). AppEntry la muestra en lugar de
 * MainScaffold. Mis locales son sus tiendas (HU-25); Catálogo (HU-26) y Promociones (HU-27) trabajan
 * sobre el local elegido arriba de cada pestaña.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OferenteScaffold(
    usuarioId: String,
    correo: String?,
    cerrandoSesion: Boolean,
    onCerrarSesion: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(OferenteTab.MIS_LOCALES) }
    val snackbar = remember { SnackbarHostState() }
    // Una instancia por cuenta, compartida por las pestañas: la lista de locales y el elegido.
    val misLocales: MisLocalesViewModel = viewModel(key = "mis_locales_$usuarioId", factory = MisLocalesViewModel.Factory)
    val irAMisLocales = { tab = OferenteTab.MIS_LOCALES }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(tab.labelRes)) }) },
        bottomBar = {
            NavigationBar {
                OferenteTab.entries.forEach { item ->
                    val seleccionado = item == tab
                    NavigationBarItem(
                        selected = seleccionado,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                imageVector = if (seleccionado) item.selectedIcon else item.unselectedIcon,
                                contentDescription = stringResource(item.labelRes)
                            )
                        },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(tab, Modifier.padding(innerPadding), animationSpec = tween(300), label = "oferente_tab") { actual ->
            when (actual) {
                OferenteTab.MIS_LOCALES -> MisLocalesScreen(snackbar, misLocales)
                OferenteTab.CATALOGO -> ConLocalElegido(misLocales) { localId ->
                    CatalogoScreen(snackbar, usuarioId, localId, onIrAMisLocales = irAMisLocales)
                }
                OferenteTab.PROMOCIONES -> ConLocalElegido(misLocales) { localId ->
                    PromocionesScreen(snackbar, usuarioId, localId, onIrAMisLocales = irAMisLocales)
                }
                OferenteTab.CUENTA -> CuentaOferente(correo, cerrandoSesion, onCerrarSesion)
            }
        }
    }
}

/**
 * Muestra arriba el selector del local y debajo el contenido de ese local. Mientras se cargan los
 * locales (o si falló la carga) se muestra lo mismo que en Mis locales.
 */
@Composable
private fun ConLocalElegido(misLocales: MisLocalesViewModel, contenido: @Composable (localId: String?) -> Unit) {
    val estado by misLocales.uiState.collectAsStateWithLifecycle()
    when (val actual = estado) {
        MisLocalesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is MisLocalesUiState.Error -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(actual.mensajeRes))
            Spacer(Modifier.height(16.dp))
            Button(onClick = misLocales::onReintentar) { Text(stringResource(R.string.mi_local_reintentar)) }
        }
        is MisLocalesUiState.Success -> Column(Modifier.fillMaxSize()) {
            if (actual.locales.isNotEmpty()) {
                SelectorLocal(
                    locales = actual.locales,
                    seleccionado = actual.seleccionado,
                    onElegir = misLocales::onSeleccionar,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
            Box(Modifier.weight(1f)) { contenido(actual.seleccionadoId) }
        }
    }
}

@Composable
private fun CuentaOferente(correo: String?, cerrandoSesion: Boolean, onCerrarSesion: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Storefront, contentDescription = null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.oferente_rol)) })
        if (!correo.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(correo, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCerrarSesion, enabled = !cerrandoSesion, modifier = Modifier.fillMaxWidth()) {
            if (cerrandoSesion) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.oferente_cerrar_sesion))
            }
        }
    }
}
