package com.cocinacapital.app.data.auth

import com.cocinacapital.app.data.model.PerfilUsuario
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Entrega al Perfil el usuario de la sesión de Supabase (mismo cliente que el Login). */
class SupabasePerfilSesion(client: SupabaseClient) : PerfilSesion {
    private val auth = client.auth
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val usuario: StateFlow<PerfilUsuario?> = auth.sessionStatus
        .map { estado -> (estado as? SessionStatus.Authenticated)?.session?.user?.aPerfil() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun actualizarNombre(nombre: String) {
        auth.updateUser { data { put("nombre", nombre) } }
    }

    override suspend fun cerrarSesion() { auth.signOut() }
}

private fun UserInfo.aPerfil(): PerfilUsuario {
    val correo = email.orEmpty()
    // "full_name" lo usaron las primeras cuentas creadas con el registro; hoy se guarda en "nombre".
    val guardado = (userMetadata?.get("nombre") ?: userMetadata?.get("full_name"))?.jsonPrimitive?.contentOrNull
    // Si aún no hay nombre guardado, usa la parte del correo antes de la @.
    val nombre = guardado ?: correo.substringBefore("@").replaceFirstChar { it.uppercase() }
    return PerfilUsuario(id = id, nombre = nombre, correo = correo)
}