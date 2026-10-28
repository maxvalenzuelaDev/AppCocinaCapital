package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.BeneficioObtenido
import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.ResultadoCanje

/**
 * Billetera del consumidor (HU-13, HU-14 y HU-16). Todo pasa por las funciones de la migración T-03,
 * que validan vigencia y cupos en Supabase; la app no escribe la tabla directamente.
 */
interface BilleteraRepository {
    /** Sus beneficios con la promoción y el local, del más reciente al más antiguo. */
    suspend fun misBeneficios(): List<BeneficioUsuario>
    /** El beneficio que ya tiene de esa promoción, o `null`. */
    suspend fun beneficioDe(promocionId: String): BeneficioObtenido?
    /** Obtiene el beneficio y descuenta un cupo; si ya lo tenía devuelve el mismo. */
    suspend fun adquirir(promocionId: String): BeneficioObtenido
    /** Quita un beneficio sin usar y devuelve su cupo a la promoción. */
    suspend fun liberar(beneficioId: String)
}

/** Validación del canje en el local (HU-15 y HU-29), solo para el oferente dueño del local. */
interface CanjeRepository {
    suspend fun consultar(codigo: String): ResultadoCanje
    suspend fun canjear(codigo: String): ResultadoCanje
}

/** Errores que lanzan las funciones de Supabase con un código conocido (ver migración T-03). */
enum class MotivoBilletera {
    SIN_SESION, PROMOCION_NO_DISPONIBLE, PROMOCION_NO_INICIADA, PROMOCION_VENCIDA, SIN_CUPOS,
    BENEFICIO_NO_ENCONTRADO, BENEFICIO_CANJEADO, NO_AUTORIZADO, CODIGO_NO_ENCONTRADO;

    companion object {
        /** El mensaje de la función es exactamente el código; cualquier otro texto no se reconoce. */
        fun desde(mensaje: String?): MotivoBilletera? = entries.firstOrNull { it.name == mensaje?.trim() }
    }
}

class BilleteraException(val motivo: MotivoBilletera) : Exception(motivo.name)
