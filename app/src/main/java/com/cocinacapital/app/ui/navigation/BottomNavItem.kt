package com.cocinacapital.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.cocinacapital.app.R

/** Destinos de la barra de navegación inferior. */
sealed class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem("home", R.string.nav_inicio, Icons.Filled.Home, Icons.Outlined.Home)
    data object Explorar : BottomNavItem("explorar", R.string.nav_explorar, Icons.Filled.Explore, Icons.Outlined.Explore)
    data object Billetera : BottomNavItem("billetera", R.string.nav_billetera, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
    data object Perfil : BottomNavItem("perfil", R.string.nav_perfil, Icons.Filled.Person, Icons.Outlined.Person)

    companion object {
        // Un destino puede consultarse antes de crear la barra inferior.
        val items: List<BottomNavItem> by lazy { listOf(Home, Explorar, Billetera, Perfil) }
    }
}
