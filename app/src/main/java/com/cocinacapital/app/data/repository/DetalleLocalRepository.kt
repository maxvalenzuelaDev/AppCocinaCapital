package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion

/** Contrato de datos del detalle de un local (RF-05). Igual que el Home, tiene versión Fake y Supabase. */
interface DetalleLocalRepository {
    /** Local con [LocalGastronomico.categoriaNombre] completo, o `null` si no existe o no está activo. */
    suspend fun getLocal(id: String): LocalGastronomico?
    suspend fun getPlatosDestacados(localId: String): List<Plato>
    suspend fun getPromocionesVigentes(localId: String): List<Promocion>
}
