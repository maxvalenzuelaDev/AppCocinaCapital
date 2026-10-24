package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion

/** Datos ficticios del detalle. Reutiliza los locales y promociones de [FakeHomeRepository]. */
class FakeDetalleLocalRepository(
    private val home: FakeHomeRepository = FakeHomeRepository()
) : DetalleLocalRepository {

    /** Descripción, dirección y horario por id de local. */
    private val info = mapOf(
        "l1" to Triple("Pescados y mariscos frescos de la caleta, con vista a la bahía.", "Av. Errázuriz 1120", "Lun a Dom 12:00–17:00"),
        "l2" to Triple("Café de especialidad y pastelería casera en pleno cerro.", "Calle Templeman 345", "Lun a Sáb 09:00–20:00"),
        "l3" to Triple("Hamburguesas de carne seleccionada y papas cortadas a mano.", "Av. San Martín 678", "Mar a Dom 13:00–23:00"),
        "l5" to Triple("Sushi y handrolls preparados al momento.", "Calle Blanco 980", "Lun a Sáb 12:30–22:30")
    )

    private val platos = listOf(
        Plato("pl1", "l1", "Pastel de jaiba", "Gratinado con queso, receta de la casa.", 12900, true),
        Plato("pl2", "l1", "Reineta a la plancha", "Con papas cocidas y ensalada chilena.", 10900, true),
        Plato("pl3", "l2", "Café de especialidad", "Grano de origen único, método a elección.", 3200, true),
        Plato("pl4", "l2", "Kuchen de frambuesa", "Porción individual.", 3500, true),
        Plato("pl5", "l3", "Burger clásica", "Carne 180 g, queso cheddar, tomate y lechuga.", 8900, true),
        Plato("pl6", "l5", "Tabla Muelle 20 piezas", "Selección del chef.", 15900, true)
    )

    override suspend fun getLocal(id: String): LocalGastronomico? {
        val local = home.getLocales().find { it.id == id } ?: return null
        val categoria = home.getCategorias().find { it.id == local.categoriaId }
        val (descripcion, direccion, horario) = info[id] ?: Triple("", null, null)
        return local.copy(
            categoriaNombre = categoria?.nombre.orEmpty(),
            descripcion = descripcion,
            direccion = direccion,
            horario = horario
        )
    }

    override suspend fun getPlatosDestacados(localId: String): List<Plato> =
        platos.filter { it.localId == localId && it.destacado }

    override suspend fun getPromocionesVigentes(localId: String): List<Promocion> =
        home.getPromocionesVigentes().filter { it.localId == localId }
}
