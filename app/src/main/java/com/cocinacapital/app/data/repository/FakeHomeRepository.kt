package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Datos ficticios (el caso exige usar solo registros ficticios y anonimizados).
 * Permite desarrollar y probar el Home sin tener Supabase configurado.
 */
class FakeHomeRepository : HomeRepository {

    private val categorias = listOf(
        Categoria("c1", "Cocina Marina"),
        Categoria("c2", "Cafetería"),
        Categoria("c3", "Burgers"),
        Categoria("c4", "Pizzería"),
        Categoria("c5", "Sushi"),
        Categoria("c6", "Pastelería")
    )

    private val locales = listOf(
        LocalGastronomico("l1", "Caleta del Puerto", "c1", "Valparaíso", null, 4.7, -33.0393, -71.6273),
        LocalGastronomico("l2", "Café Cerro Alegre", "c2", "Valparaíso", null, 4.5, -33.0425, -71.6285),
        LocalGastronomico("l3", "Burger Pacífico", "c3", "Viña del Mar", null, 4.2, -33.0245, -71.5518),
        LocalGastronomico("l4", "Pizza Reñaca", "c4", "Viña del Mar", null, 4.4, -32.9627, -71.5498),
        LocalGastronomico("l5", "Sushi Muelle", "c5", "Valparaíso", null, 4.6, -33.0360, -71.6290),
        LocalGastronomico("l6", "Dulce Quinta", "c6", "Viña del Mar", null, 4.8, -33.0200, -71.5520)
    )

    override suspend fun getCategorias(): List<Categoria> { simularRed(); return categorias }
    override suspend fun getLocales(): List<LocalGastronomico> { simularRed(); return locales }

    override suspend fun getPromocionesVigentes(): List<Promocion> {
        simularRed()
        val ahora = Instant.now()
        return listOf(
            promo("p1", "l1", "2x1 en pastel de jaiba", "Válido en horario de almuerzo, de lunes a jueves.", 50, ahora.plus(5, ChronoUnit.HOURS)),
            promo("p2", "l2", "Café + kuchen", "Lleva tu café de especialidad con kuchen del día.", 30, ahora.plus(3, ChronoUnit.HOURS)),
            promo("p3", "l3", "Combo burger tarde", "Burger clásica, papas y bebida entre las 15:00 y 18:00.", 25, ahora.plus(1, ChronoUnit.DAYS)),
            promo("p4", "l5", "Handroll de salmón gratis", "Por la compra de una tabla de 20 piezas.", 20, ahora.plus(2, ChronoUnit.DAYS))
        )
    }

    private fun promo(id: String, localId: String, titulo: String, desc: String, pct: Int, hasta: Instant) =
        Promocion(id, localId, titulo, desc, pct, Instant.now().minus(1, ChronoUnit.DAYS).toString(), hasta.toString())

    private suspend fun simularRed() = delay(700)
}
