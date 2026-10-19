package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion

/**
 * Contrato de datos del Home. El ViewModel solo conoce esta interfaz, así podemos
 * cambiar entre datos de prueba (Fake) y Supabase sin tocar la UI.
 */
interface HomeRepository {
    suspend fun getCategorias(): List<Categoria>
    suspend fun getLocales(): List<LocalGastronomico>
    suspend fun getPromocionesVigentes(): List<Promocion>
}
