package com.cocinacapital.app.data.auth

import com.cocinacapital.app.data.model.PerfilUsuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Puente entre el Perfil y el módulo de Login/Registro (hecho por los compañeros).
 * Cuando suban su código, se crea una implementación real y se cambia en AppContainer.
 */
interface PerfilSesion {
    /** Usuario actual, o `null` si no hay sesión. */
    val usuario: StateFlow<PerfilUsuario?>
    suspend fun actualizarNombre(nombre: String)
    suspend fun cerrarSesion()
}

/** Usuario ficticio para desarrollar sin Login. */
class FakePerfilSesion : PerfilSesion {
    private val _usuario = MutableStateFlow<PerfilUsuario?>(
        PerfilUsuario(id = "usuario-demo", nombre = "Usuario Demo", correo = "usuario.demo@ejemplo.cl")
    )
    override val usuario: StateFlow<PerfilUsuario?> = _usuario.asStateFlow()

    override suspend fun actualizarNombre(nombre: String) {
        _usuario.update { it?.copy(nombre = nombre) }
    }

    /** No hace nada a propósito: la versión real llama a supabase.auth.signOut() y la app vuelve al Login. */
    override suspend fun cerrarSesion() = Unit
}

/*
 * Ejemplo de implementación real con supabase-kt (requiere el módulo auth-kt):
 *
 * class SupabaseAuthGateway(private val supabase: SupabaseClient) : AuthGateway {
 *     // Mapear supabase.auth.currentUserOrNull() a PerfilUsuario (id, correo, nombre en user_metadata)
 *     override suspend fun actualizarNombre(nombre: String) {
 *         supabase.auth.updateUser { data = buildJsonObject { put("nombre", nombre) } }
 *     }
 *     override suspend fun cerrarSesion() = supabase.auth.signOut()
 * }
 */
