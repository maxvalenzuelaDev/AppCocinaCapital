package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.LocalGastronomico

class FakeDetallePromocionRepository(
    private val home: HomeRepository = FakeHomeRepository(),
    private val locales: DetalleLocalRepository = FakeDetalleLocalRepository()
) : DetallePromocionRepository {
    override suspend fun getPromocion(id: String): PromocionDetalle? =
        home.getPromocionesVigentes().firstOrNull { it.id == id }?.let {
            PromocionDetalle(it, cuposConfigurados = false, horarioConfigurado = false)
        }

    override suspend fun getLocal(localId: String): LocalGastronomico? =
        locales.getLocal(localId)
}
