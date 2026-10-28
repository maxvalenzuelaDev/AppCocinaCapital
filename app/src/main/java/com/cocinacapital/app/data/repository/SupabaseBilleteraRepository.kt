package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.BeneficioObtenido
import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.ResultadoCanje
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseBilleteraRepository(private val supabase: SupabaseClient) : BilleteraRepository {

    override suspend fun misBeneficios(): List<BeneficioUsuario> = conMotivo {
        supabase.postgrest.rpc("mis_beneficios").decodeList<BeneficioUsuario>()
    }

    /** RLS ("Ver mis beneficios") limita la consulta a las filas de quien tiene sesión. */
    override suspend fun beneficioDe(promocionId: String): BeneficioObtenido? =
        supabase.from("beneficios_usuario")
            .select { filter { eq("promocion_id", promocionId) } }
            .decodeSingleOrNull<BeneficioObtenido>()

    override suspend fun adquirir(promocionId: String): BeneficioObtenido = conMotivo {
        supabase.postgrest.rpc("adquirir_beneficio", buildJsonObject { put("p_promocion_id", promocionId) })
            .decodeAs<BeneficioObtenido>()
    }

    override suspend fun liberar(beneficioId: String) = conMotivo {
        supabase.postgrest.rpc("liberar_beneficio", buildJsonObject { put("p_beneficio_id", beneficioId) })
        Unit
    }
}

class SupabaseCanjeRepository(private val supabase: SupabaseClient) : CanjeRepository {

    override suspend fun consultar(codigo: String): ResultadoCanje = llamar("consultar_beneficio", codigo)

    override suspend fun canjear(codigo: String): ResultadoCanje = llamar("canjear_beneficio", codigo)

    private suspend fun llamar(funcion: String, codigo: String): ResultadoCanje = conMotivo {
        supabase.postgrest.rpc(funcion, buildJsonObject { put("p_codigo", codigo) })
            .decodeSingle<ResultadoCanje>()
    }
}

/**
 * Traduce los errores conocidos de las funciones a [BilleteraException]. Usa `error` (el mensaje de
 * Postgres) y no `message`, que en supabase-kt incluye la URL y las cabeceras de la petición.
 */
private inline fun <T> conMotivo(llamada: () -> T): T = try {
    llamada()
} catch (error: PostgrestRestException) {
    throw MotivoBilletera.desde(error.error)?.let(::BilleteraException) ?: error
}
