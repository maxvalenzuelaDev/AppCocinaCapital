package com.cocinacapital.app.ui.registro

import androidx.annotation.StringRes

/**
 * Estado de la pantalla de registro (HU-01 / RF-01).
 *
 * Almacena los datos ingresados, errores de validación
 * y el resultado de la solicitud de registro.
 */
data class RegistroUiState(

    // ---- Datos ingresados por el usuario ----
    val nombre: String = "",
    val correo: String = "",
    val contrasena: String = "",

    // ---- Errores de validación (textos en res/values/acceso.xml) ----
    @StringRes val errorNombre: Int? = null,
    @StringRes val errorCorreo: Int? = null,
    @StringRes val errorContrasena: Int? = null,

    // ---- Estado del formulario y registro ----
    val formularioValido: Boolean = false,
    @StringRes val mensaje: Int? = null,

    // Indica si se está procesando el registro en Supabase.
    // Se utiliza para mostrar el indicador de carga y desactivar el botón.
    val cargando: Boolean = false,
    // Indica si Supabase aceptó la solicitud de registro.
    // Permite mostrar el mensaje de confirmación por correo electrónico.
    val registroExitoso: Boolean = false
)