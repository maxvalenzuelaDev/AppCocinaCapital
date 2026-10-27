package com.cocinacapital.app.ui.registro

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocinacapital.app.R
import com.cocinacapital.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del registro de consumidores (HU-01 / RF-01).
 *
 * Administra los datos del formulario mediante StateFlow,
 * valida nombre, correo y contraseña, y solicita el registro
 * a Supabase mediante AuthRepository.
 *
 * La pantalla solo observa los cambios del estado.
 */
class RegistroViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroUiState())

    // ---- Estado interno del formulario ----
    val uiState: StateFlow<RegistroUiState> =
        _uiState.asStateFlow()

    // ---------------- Acciones de la UI ----------------

    // Vacía el formulario al salir del registro, para que no queden datos (ni la contraseña)
    // si el usuario vuelve a entrar más tarde.
    fun reiniciar() {
        _uiState.value = RegistroUiState()
    }

    // Actualiza el nombre y elimina su error anterior.
    fun onNombreChange(nombre: String) {
        _uiState.update {
            it.copy(
                nombre = nombre,
                errorNombre = null,
                mensaje = null,
                formularioValido = false,
                registroExitoso = false
            )
        }
    }

    // Actualiza el correo y limpia los mensajes anteriores.
    fun onCorreoChange(correo: String) {
        _uiState.update {
            it.copy(
                correo = correo,
                errorCorreo = null,
                mensaje = null,
                formularioValido = false,
                registroExitoso = false
            )
        }
    }

    // Actualiza la contraseña y reinicia su validación.
    fun onContrasenaChange(contrasena: String) {
        _uiState.update {
            it.copy(
                contrasena = contrasena,
                errorContrasena = null,
                mensaje = null,
                formularioValido = false,
                registroExitoso = false
            )
        }
    }

    /**
     * Valida los campos antes de enviar los datos.
     * Si existe algún error, se muestra en el formulario.
     * Si los datos son válidos, comienza el registro.
     */
    fun validarRegistro() {
        if (_uiState.value.cargando) return

        val datos = _uiState.value

        val errorNombre = if (datos.nombre.isBlank()) {
            R.string.registro_error_nombre
        } else null

        val errorCorreo = when {
            datos.correo.isBlank() ->
                R.string.registro_error_correo_vacio

            !Patterns.EMAIL_ADDRESS.matcher(
                datos.correo.trim()
            ).matches() ->
                R.string.registro_error_correo_invalido

            else -> null
        }

        val errorContrasena = when {
            datos.contrasena.isBlank() ->
                R.string.registro_error_contrasena_vacia

            datos.contrasena.length < 8 ->
                R.string.registro_error_contrasena_corta

            else -> null
        }

        val valido = errorNombre == null &&
                errorCorreo == null &&
                errorContrasena == null

        _uiState.update {
            it.copy(
                errorNombre = errorNombre,
                errorCorreo = errorCorreo,
                errorContrasena = errorContrasena,
                formularioValido = valido,
                mensaje = null,
                registroExitoso = false
            )
        }

        if (!valido) return

        // ---------------- Registro en Supabase ----------------

        // Ejecuta la solicitud sin bloquear la interfaz.
        viewModelScope.launch {
            _uiState.update {
                it.copy(cargando = true)
            }

            // Envía los datos validados al servicio de autenticación.
            try {
                authRepository.registrarUsuario(
                    nombre = datos.nombre.trim(),
                    correo = datos.correo.trim(),
                    contrasena = datos.contrasena
                )

                _uiState.update {
                    it.copy(
                        cargando = false,
                        registroExitoso = true,
                        contrasena = "",
                        mensaje = R.string.registro_exitoso
                    )
                }

            // Informa al usuario si falla la solicitud de registro.
            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                // Registra el error técnico para identificar
                // por qué falló el registro en Supabase.
                android.util.Log.e(
                    "CocinaCapitalRegistro",
                    "Error al registrar usuario",
                    e
                )

                // Identifica el tipo de error devuelto por Supabase.
                val mensajeError = when {
                    e.message?.contains("over_email_send_rate_limit", ignoreCase = true) == true ->
                        R.string.registro_error_limite_correos

                    e.message?.contains("email_address_invalid", ignoreCase = true) == true ->
                        R.string.registro_error_correo_rechazado

                    e.message?.contains("user_already_exists", ignoreCase = true) == true ->
                        R.string.registro_error_ya_existe

                    else ->
                        R.string.registro_error_desconocido
                }

                // Actualiza el estado para mostrar el mensaje correspondiente.
                _uiState.update {
                    it.copy(
                        cargando = false,
                        mensaje = mensajeError
                    )
                }
            }
        }
    }
}
