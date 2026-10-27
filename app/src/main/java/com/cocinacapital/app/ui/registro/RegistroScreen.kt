package com.cocinacapital.app.ui.registro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R

/**
 * Pantalla de registro conectada al ViewModel.
 *
 * Observa el estado del formulario y envía las acciones
 * del usuario al RegistroViewModel.
 */
@Composable
fun RegistroScreen(
    onVolverLogin: () -> Unit = {},
    viewModel: RegistroViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RegistroContent(
        state = state,
        onNombreChange = viewModel::onNombreChange,
        onCorreoChange = viewModel::onCorreoChange,
        onContrasenaChange = viewModel::onContrasenaChange,
        onRegistrar = viewModel::validarRegistro,
        onVolverLogin = onVolverLogin
    )
}

/**
 * Interfaz visual del registro.
 *
 * Muestra los campos, mensajes de error y botón de registro.
 * Utiliza los colores del tema compartido con el login.
 */
@Composable
fun RegistroContent(
    state: RegistroUiState,
    onNombreChange: (String) -> Unit,
    onCorreoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onRegistrar: () -> Unit,
    onVolverLogin: () -> Unit
) {
    var mostrarContrasena by remember {
        mutableStateOf(false)
    }

    // Colores compartidos con el tema de Cocina Capital.
    val colores = MaterialTheme.colorScheme

    val coloresCampos = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colores.onSurface,
        unfocusedTextColor = colores.onSurface,
        focusedBorderColor = colores.primary,
        unfocusedBorderColor = colores.outline,
        focusedLabelColor = colores.primary,
        unfocusedLabelColor = colores.onSurfaceVariant,
        focusedPlaceholderColor = colores.onSurfaceVariant,
        unfocusedPlaceholderColor = colores.onSurfaceVariant,
        focusedContainerColor = colores.background,
        unfocusedContainerColor = colores.background,
        cursorColor = colores.primary,
        errorTextColor = colores.onSurface,
        errorBorderColor = colores.error,
        errorLabelColor = colores.error,
        errorCursorColor = colores.error
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colores.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ---------------- Encabezado de Cocina Capital ----------------
            Text(
                text = stringResource(R.string.marca_nombre),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colores.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.registro_titulo),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = colores.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.registro_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ---------------- Campo de nombre completo ----------------
            OutlinedTextField(
                value = state.nombre,
                onValueChange = onNombreChange,
                label = { Text(stringResource(R.string.registro_nombre)) },
                placeholder = { Text(stringResource(R.string.registro_nombre_ejemplo)) },
                singleLine = true,
                // Editable tras enviar: si el correo estaba mal, se corrige y se vuelve a registrar.
                enabled = !state.cargando,
                isError = state.errorNombre != null,
                supportingText = {
                    state.errorNombre?.let {
                        Text(stringResource(it), color = colores.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = coloresCampos
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---------------- Campo de correo electrónico ----------------
            OutlinedTextField(
                value = state.correo,
                onValueChange = onCorreoChange,
                label = { Text(stringResource(R.string.campo_correo)) },
                placeholder = { Text(stringResource(R.string.campo_correo_ejemplo)) },
                singleLine = true,
                // Editable tras enviar: si el correo estaba mal, se corrige y se vuelve a registrar.
                enabled = !state.cargando,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                isError = state.errorCorreo != null,
                supportingText = {
                    state.errorCorreo?.let {
                        Text(stringResource(it), color = colores.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = coloresCampos
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---------------- Campo de contraseña ----------------
            OutlinedTextField(
                value = state.contrasena,
                onValueChange = onContrasenaChange,
                label = { Text(stringResource(R.string.campo_contrasena)) },
                singleLine = true,
                // Editable tras enviar: si el correo estaba mal, se corrige y se vuelve a registrar.
                enabled = !state.cargando,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                visualTransformation = if (mostrarContrasena) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            mostrarContrasena = !mostrarContrasena
                        }
                    ) {
                        Text(
                            text = stringResource(if (mostrarContrasena) R.string.contrasena_ocultar else R.string.contrasena_mostrar),
                            color = colores.primary
                        )
                    }
                },
                isError = state.errorContrasena != null,
                supportingText = {
                    Text(
                        text = stringResource(state.errorContrasena ?: R.string.registro_contrasena_ayuda),
                        color = if (state.errorContrasena != null) {
                            colores.error
                        } else {
                            colores.onSurfaceVariant
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = coloresCampos
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---------------- Botón de registro y estado de carga ----------------
            Button(
                onClick = onRegistrar,
                enabled = !state.cargando && !state.registroExitoso,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colores.primary,
                    contentColor = colores.onPrimary
                )
            ) {
                if (state.cargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colores.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.registro_boton),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ---------------- Mensajes del resultado del registro ----------------
            state.mensaje?.let { mensaje ->
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(mensaje),
                    color = if (state.registroExitoso) {
                        androidx.compose.ui.graphics.Color(0xFF81C995)
                    } else {
                        colores.error
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Tras enviar, se avisa que los datos se pueden corregir (por ejemplo, un correo mal escrito).
            if (state.registroExitoso) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.registro_corregir_datos),
                    color = colores.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---------------- Navegación hacia inicio de sesión ----------------
            TextButton(onClick = onVolverLogin) {
                Text(
                    text = stringResource(R.string.registro_ya_tienes_cuenta),
                    color = colores.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

