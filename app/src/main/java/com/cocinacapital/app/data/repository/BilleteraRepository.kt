package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.model.PromocionGuardada
import com.cocinacapital.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repositorio de la billetera de Cocina Capital.
 *
 * Permite guardar, consultar y eliminar promociones
 * asociadas al usuario autenticado en Supabase.
 */
class BilleteraRepository {

    // Utiliza el cliente Supabase compartido del proyecto.
    private val supabase = SupabaseProvider.client

    /**
     * Obtiene el ID del usuario que inició sesión.
     */
    private fun obtenerUsuarioId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException(
                "Debes iniciar sesión para utilizar la billetera."
            )
    }

    /**
     * Consulta las promociones guardadas del consumidor.
     */
    suspend fun obtenerBeneficios(): List<BeneficioUsuario> {
        val usuarioId = obtenerUsuarioId()

        return supabase.from("beneficios_usuario")
            .select {
                filter {
                    eq("usuario_id", usuarioId)
                }
            }
            .decodeList<BeneficioUsuario>()
    }

    /**
     * Guarda una promoción en la billetera del consumidor.
     *
     * Supabase genera automáticamente el ID del registro
     * y la fecha en que se guardó.
     */
    suspend fun guardarBeneficio(promocionId: String) {
        val usuarioId = obtenerUsuarioId()

        val nuevoBeneficio = buildJsonObject {
            put("usuario_id", usuarioId)
            put("promocion_id", promocionId)
        }

        supabase.from("beneficios_usuario")
            .insert(nuevoBeneficio)
    }

    /**
     * Elimina una promoción guardada por el consumidor.
     */
    suspend fun eliminarBeneficio(promocionId: String) {
        val usuarioId = obtenerUsuarioId()

        supabase.from("beneficios_usuario")
            .delete {
                filter {
                    eq("usuario_id", usuarioId)
                    eq("promocion_id", promocionId)
                }
            }
    }

    /**
     * Obtiene las promociones guardadas con su
     * información real y el nombre del local.
     */
    suspend fun obtenerPromocionesGuardadas(): List<PromocionGuardada> {

        // Primero consultamos las promociones guardadas
        // por el consumidor que inició sesión.
        val beneficios = obtenerBeneficios()

        val resultado = mutableListOf<PromocionGuardada>()

        for (beneficio in beneficios) {

            // Busca los datos reales de la promoción.
            val promocion = supabase.from("promociones")
                .select {
                    filter {
                        eq("id", beneficio.promocionId)
                    }
                }
                .decodeSingleOrNull<Promocion>()

            // Si la promoción ya no existe o no es visible,
            // no la mostramos en la lista.
            if (promocion == null) {
                continue
            }

            // Busca el local asociado a la promoción.
            val local = supabase.from("locales")
                .select {
                    filter {
                        eq("id", promocion.localId)
                    }
                }
                .decodeSingleOrNull<LocalGastronomico>()

            // Une los datos del beneficio, promoción y local.
            resultado.add(
                PromocionGuardada(
                    beneficio = beneficio,
                    promocion = promocion,
                    nombreLocal = local?.nombre ?: "Local no disponible"
                )
            )
        }

        return resultado
    }
}
