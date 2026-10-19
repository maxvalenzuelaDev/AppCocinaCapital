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

/** Destinos de la barra de navegación inferior. */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem("home", "Inicio", Icons.Filled.Home, Icons.Outlined.Home)
    data object Explorar : BottomNavItem("explorar", "Explorar", Icons.Filled.Explore, Icons.Outlined.Explore)
    data object Billetera : BottomNavItem("billetera", "Billetera", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
    data object Perfil : BottomNavItem("perfil", "Perfil", Icons.Filled.Person, Icons.Outlined.Person)

    companion object {
        val items = listOf(Home, Explorar, Billetera, Perfil)
    }
}
