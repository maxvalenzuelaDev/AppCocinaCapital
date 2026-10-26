package com.cocinacapital.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PresupuestoPersonaTest {
    @Test fun clientExampleUsesTheAveragePerPersonWithinAnInclusiveBudget() {
        val presupuesto = leerPresupuesto("15000", "20000").presupuesto!!
        assertTrue(presupuesto.incluye(17500))
        assertTrue(presupuesto.incluye(15000)); assertTrue(presupuesto.incluye(20000))
        assertFalse(presupuesto.incluye(14999)); assertFalse(presupuesto.incluye(20001))
    }
    @Test fun customHighAmountsAndUnboundedUpperLimitsAreNotRestrictedByPresets() {
        assertTrue(leerPresupuesto("200000", "500000").presupuesto!!.incluye(350000))
        assertTrue(leerPresupuesto("200000", "").presupuesto!!.incluye(Long.MAX_VALUE))
        assertTrue(leerPresupuesto("", "500000").presupuesto!!.incluye(1))
    }
    @Test fun blankFieldsRemoveTheBudgetAndZeroIsNotAnUnknownAverage() {
        assertNull(leerPresupuesto("", "").presupuesto)
        assertNull(leerPresupuesto("0", "").presupuesto)
        val cero = leerPresupuesto("", "0").presupuesto!!
        assertFalse(cero.incluye(0)); assertFalse(cero.incluye(1))
    }
    @Test fun invalidAmountsAndOverflowDoNotSilentlyBecomeUnbounded() {
        listOf("-1", "15.5", "texto", "999999999999999999999999").forEach {
            val result = leerPresupuesto("", it)
            assertEquals(ErrorPresupuesto.IMPORTE_INVALIDO, result.error)
            assertNull(result.presupuesto)
        }
    }
    @Test fun invertedBoundsCannotBeApplied() {
        val result = leerPresupuesto("50000", "20000")
        assertEquals(ErrorPresupuesto.ORDEN_INVALIDO, result.error)
        assertNull(result.presupuesto)
    }
    @Test fun backendReadsTheDeclaredAverageAndKeepsOldCatalogsUnknown() {
        val json = Json { ignoreUnknownKeys = true }
        val base = "\"id\":\"a\",\"nombre\":\"Local\",\"categoria_id\":\"c\",\"comuna\":\"Viña\""
        assertNull(json.decodeFromString<LocalGastronomico>("{$base,\"precio\":3900}").ticketPromedioPersona)
        assertEquals(17500L, json.decodeFromString<LocalGastronomico>("{$base,\"ticket_promedio_persona\":17500}").ticketPromedioPersona)
    }
}
