package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repositorio de autenticación de Cocina Capital.
 *
 * Utiliza el cliente Supabase compartido por la aplicación
 * para registrar consumidores con correo y contraseña.
 */
class AuthRepository {

    private val supabase = SupabaseProvider.client

    /**
     * Solicita la creación de una cuenta en Supabase Auth.
     *
     * Envía nombre, correo y contraseña. El nombre se almacena
     * como metadato del usuario.
     *
     * Supabase administra las credenciales y la confirmación
     * del correo electrónico.
     *
     * Puede lanzar una excepción si la solicitud falla.
     */
    suspend fun registrarUsuario(
        nombre: String,
        correo: String,
        contrasena: String
    ) {
        supabase.auth.signUpWith(Email) {
            email = correo.trim()
            password = contrasena

            // Guarda el nombre completo como metadato de la cuenta.
            data = buildJsonObject {
                put("full_name", nombre.trim())
            }
        }
    }
}
