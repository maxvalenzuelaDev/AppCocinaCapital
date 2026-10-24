package com.cocinacapital.app.ui.registro

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

    // ---- Errores de validación ----
    val errorNombre: String? = null,
    val errorCorreo: String? = null,
    val errorContrasena: String? = null,

    // ---- Estado del formulario y registro ----
    val formularioValido: Boolean = false,
    val mensaje: String? = null,

    // Indica si se está procesando el registro en Supabase.
    // Se utiliza para mostrar el indicador de carga y desactivar el botón.
    val cargando: Boolean = false,
    // Indica si Supabase aceptó la solicitud de registro.
    // Permite mostrar el mensaje de confirmación por correo electrónico.
    val registroExitoso: Boolean = false
)