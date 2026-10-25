package com.cocinacapital.app.ui.explore

import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.ui.home.UbicacionUsuario
import org.junit.Test
import org.junit.Assert.*

class LocalesCercanosTest {
    private val origin = UbicacionUsuario("", -33.02, -71.55)
    private val data = listOf(
        LocalGastronomico("a", "Café Alegre", "cafe", "Valparaíso", valoracion = 4.8, latitud = -33.04, longitud = -71.62,
            categoriaNombre = "Cafetería", ticketPromedioPersona = 17500),
        LocalGastronomico("b", "Pizza Viña", "pizza", "Viña del Mar", valoracion = 4.2, latitud = -33.02, longitud = -71.55,
            categoriaNombre = "Pizzería", ticketPromedioPersona = 350000),
        LocalGastronomico("c", "Sin coordenadas", "cafe", "Viña del Mar")
    )
    @Test fun radiusExcludesFarAwayAndMissingCoordinates() {
        assertEquals(listOf("b"), localesCercanos(data, FiltrosExplorar(), origin, PreferenciasUsuario()).map { it.id })
    }
    @Test fun expandingRadiusKeepsNearestFirst() {
        assertEquals(listOf("b", "a"), localesCercanos(data, FiltrosExplorar(radioKm = 20), origin, PreferenciasUsuario()).map { it.id })
    }
    @Test fun noOriginHasNoNearbyResults() { assertTrue(localesCercanos(data, FiltrosExplorar(), null, PreferenciasUsuario()).isEmpty()) }
    @Test fun textAndCategoriesAreCombinedWithoutAccents() {
        assertEquals(listOf("a"), localesCercanos(data, FiltrosExplorar("CAFE", "cafe", 20), origin, PreferenciasUsuario()).map { it.id })
        assertTrue(localesCercanos(data, FiltrosExplorar("cafe", "pizza", 20), origin, PreferenciasUsuario()).isEmpty())
    }
    @Test fun invalidCoordinatesAreNotShownOnTheMap() {
        assertTrue(localesCercanos(listOf(data[0].copy(latitud = 120.0)), FiltrosExplorar(radioKm = 20), origin, PreferenciasUsuario()).isEmpty())
    }
    @Test fun textKitchenBudgetAndDistanceAreIntersected() {
        val filtro = FiltrosExplorar("CAFE", "cafe", 20, presupuesto = PresupuestoPersona(15000, 20000))
        assertEquals(listOf("a"), localesCercanos(data, filtro, origin, PreferenciasUsuario()).map { it.id })
        assertTrue(localesCercanos(data, filtro.copy(radioKm = 5), origin, PreferenciasUsuario()).isEmpty())
        assertTrue(localesCercanos(data, filtro.copy(presupuesto = PresupuestoPersona(50000)), origin, PreferenciasUsuario()).isEmpty())
    }
    @Test fun highCustomBudgetsAndOpenUpperLimitsAreSupported() {
        assertEquals(listOf("b"), localesCercanos(data, FiltrosExplorar(presupuesto = PresupuestoPersona(200000, 500000)), origin, PreferenciasUsuario()).map { it.id })
        assertEquals(listOf("b"), localesCercanos(data, FiltrosExplorar(presupuesto = PresupuestoPersona(300000)), origin, PreferenciasUsuario()).map { it.id })
    }
    @Test fun unknownOrInvalidAveragesAreExcludedOnlyWhenBudgetIsApplied() {
        val locales = listOf(data[0].copy(ticketPromedioPersona = null), data[1].copy(ticketPromedioPersona = 0))
        assertEquals(2, localesCercanos(locales, FiltrosExplorar(radioKm = 20), origin, PreferenciasUsuario()).size)
        assertTrue(localesCercanos(locales, FiltrosExplorar(radioKm = 20, presupuesto = PresupuestoPersona(maximo = 500000)), origin, PreferenciasUsuario()).isEmpty())
    }
    @Test fun filteringDoesNotAlterTheAverageDeclaredByTheCommerce() {
        val filtered = localesCercanos(data, FiltrosExplorar(radioKm = 20, presupuesto = PresupuestoPersona(15000, 20000)), origin, PreferenciasUsuario())
        assertEquals(17500L, filtered.single().ticketPromedioPersona)
        assertEquals(350000L, data[1].ticketPromedioPersona)
    }
}
