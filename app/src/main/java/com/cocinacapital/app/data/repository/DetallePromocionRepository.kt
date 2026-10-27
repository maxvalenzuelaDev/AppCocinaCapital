package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion

/** Permite distinguir una base antigua de las reglas explícitas de HU-27. */
data class PromocionDetalle(
    val promocion: Promocion,
    val cuposConfigurados: Boolean,
    val horarioConfigurado: Boolean
)

interface DetallePromocionRepository {
    /** Consulta por id, incluyendo promociones cuya vigencia ya terminó. */
    suspend fun getPromocion(id: String): PromocionDetalle?
    suspend fun getLocal(localId: String): LocalGastronomico?
}
