package com.cocinacapital.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavItemTest {
    @Test fun resolvingExploreBeforeBuildingTheNavigationKeepsAllDestinationsAvailable() {
        val explore = BottomNavItem.Explorar
        assertEquals("explorar", explore.route)
        assertEquals(listOf("home", "explorar", "billetera", "perfil"), BottomNavItem.items.map { it.route })
        assertEquals(explore, BottomNavItem.items[1])
    }
}
