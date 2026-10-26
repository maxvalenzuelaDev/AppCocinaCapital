package com.cocinacapital.app.data.auth

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class RolTest {
    @Test fun `app_metadata con rol oferente es oferente`() {
        assertEquals(Rol.OFERENTE, rolDesde(buildJsonObject { put("rol", "oferente") }))
        assertEquals(Rol.OFERENTE, rolDesde(buildJsonObject { put("rol", " Oferente ") }))
    }

    @Test fun `sin rol o con otro valor es consumidor`() {
        assertEquals(Rol.CONSUMIDOR, rolDesde(null))
        assertEquals(Rol.CONSUMIDOR, rolDesde(buildJsonObject { put("provider", "email") }))
        assertEquals(Rol.CONSUMIDOR, rolDesde(buildJsonObject { put("rol", "consumidor") }))
        assertEquals(Rol.CONSUMIDOR, rolDesde(buildJsonObject { put("rol", "admin") }))
        assertEquals(Rol.CONSUMIDOR, rolDesde(buildJsonObject { put("rol", JsonPrimitive(1)) }))
    }
}
