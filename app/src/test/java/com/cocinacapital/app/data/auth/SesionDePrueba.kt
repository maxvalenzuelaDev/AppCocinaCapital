package com.cocinacapital.app.data.auth

import com.cocinacapital.app.data.model.PerfilUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Sesión para pruebas: permite cambiar de cuenta o cerrar sesión (la de `FakePerfilSesion` es fija). */
class SesionDePrueba(inicial: PerfilUsuario? = ANA) : PerfilSesion {
    override val usuario = MutableStateFlow(inicial)
    var cerroSesion = false

    override suspend fun actualizarNombre(nombre: String) { usuario.update { it?.copy(nombre = nombre) } }
    override suspend fun cerrarSesion() { cerroSesion = true; usuario.value = null }

    companion object {
        val ANA = PerfilUsuario(id = "ana", nombre = "Ana Pérez", correo = "ana@ejemplo.cl")
        val LUIS = PerfilUsuario(id = "luis", nombre = "Luis Soto", correo = "luis@ejemplo.cl")
    }
}
