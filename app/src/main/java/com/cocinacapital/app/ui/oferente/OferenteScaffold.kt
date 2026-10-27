package com.cocinacapital.app.ui.oferente

import androidx.annotation.StringRes
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
    MI_LOCAL(R.string.oferente_nav_local, Icons.Filled.Storefront, Icons.Outlined.Storefront),
    CATALOGO(R.string.oferente_nav_catalogo, Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
    PROMOCIONES(R.string.oferente_nav_promociones, Icons.Filled.LocalOffer, Icons.Outlined.LocalOffer),
    CUENTA(R.string.oferente_nav_cuenta, Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * Estructura principal para cuentas con rol oferente (HU-04). AppEntry la muestra en lugar de
 * MainScaffold. Mi local es el perfil del local (HU-25), Catálogo sus platos (HU-26) y Promociones
 * las que publica (HU-27).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OferenteScaffold(
    usuarioId: String,
    correo: String?,
    cerrandoSesion: Boolean,
    onCerrarSesion: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(OferenteTab.MI_LOCAL) }
    val snackbar = remember { SnackbarHostState() }
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
                OferenteTab.MI_LOCAL -> MiLocalScreen(snackbar, usuarioId)
                OferenteTab.CATALOGO -> CatalogoScreen(snackbar, usuarioId, onIrAMiLocal = { tab = OferenteTab.MI_LOCAL })
                OferenteTab.PROMOCIONES -> PromocionesScreen(snackbar, usuarioId, onIrAMiLocal = { tab = OferenteTab.MI_LOCAL })
                OferenteTab.CUENTA -> CuentaOferente(correo, cerrandoSesion, onCerrarSesion)
            }
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
