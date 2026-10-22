package com.cocinacapital.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** El equipo conecta aquí su pantalla de registro, por ejemplo con navController.navigate. */
@Composable fun LoginScreen(onRegisterClick: () -> Unit, model: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val state by model.uiState.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AuthPage(state, model, onRegisterClick)
    }
}

@Composable private fun AuthPage(state: AuthUiState, model: AuthViewModel, onRegisterClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(26.dp))
        Brand()
        Spacer(Modifier.height(46.dp))
        if (state.restoring) {
            Column(Modifier.fillMaxWidth().padding(vertical = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                Spacer(Modifier.height(20.dp))
                Text("Preparando tu mesa…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            key(state.screen) {
                when (state.screen) {
                    AuthScreen.LOGIN -> LoginForm(state, model, onRegisterClick)
                    AuthScreen.SESSION -> Unit // AppEntry muestra el Home del equipo.
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(21.dp))
        Text("DESCUBRE  ·  ELIGE  ·  DISFRUTA", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable private fun Brand() {
    val logoColor = MaterialTheme.colorScheme.onPrimary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(logoColor, 180f, 180f, false, Offset(size.width * .12f, size.height * .28f), Size(size.width * .76f, size.height * .65f), style = stroke)
                drawLine(logoColor, Offset(size.width * .08f, size.height * .63f), Offset(size.width * .92f, size.height * .63f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(logoColor, Offset(size.width * .26f, size.height * .79f), Offset(size.width * .74f, size.height * .79f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(logoColor, radius = 1.7.dp.toPx(), center = Offset(size.width * .5f, size.height * .2f))
            }
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text("COCINA CAPITAL", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("GASTRONOMÍA LOCAL", fontSize = 9.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Heading(kicker: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(20.dp).height(1.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(10.dp))
            Text(kicker, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 37.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(14.dp))
        Text(subtitle, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable private fun LoginForm(state: AuthUiState, model: AuthViewModel, onRegisterClick: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Las contraseñas no se guardan en SavedStateHandle ni rememberSaveable.
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = { focus.clearFocus(); model.login(email, password) }
    Heading("TU LUGAR EN LA MESA", "Qué bueno\nvolver a verte.", "Inicia sesión y encuentra tu próxima\nexperiencia gastronómica.")
    Field("Correo electrónico", "tu@correo.com", email, "email", !state.busy, KeyboardType.Email) {
        email = it; model.dismissError()
    }
    Spacer(Modifier.height(19.dp))
    PasswordField("Contraseña", password, "password", !state.busy, onDone = submit) {
        password = it; model.dismissError()
    }
    Feedback(state)
    Spacer(Modifier.height(26.dp))
    PrimaryButton("Iniciar sesión", "loginButton", state.busy, submit)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("¿No tienes cuenta?", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRegisterClick, enabled = !state.busy, modifier = Modifier.testTag("openRegister")) {
            Text("Crear cuenta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun Field(
    label: String, hint: String, value: String, tag: String, enabled: Boolean,
    type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth().testTag(tag), enabled = enabled,
            singleLine = true, placeholder = { Text(hint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
            colors = fieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 15.sp))
    }
}

@Composable private fun PasswordField(
    label: String, value: String, tag: String, enabled: Boolean, hint: String = "Tu contraseña",
    onDone: (() -> Unit)? = null, onChange: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth().testTag(tag), enabled = enabled, singleLine = true,
            placeholder = { Text(hint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }, shape = RoundedCornerShape(12.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (onDone == null) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }, enabled = enabled, modifier = Modifier.testTag("${tag}Toggle")) {
                    Text(if (visible) "Ocultar" else "Mostrar", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }, colors = fieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 15.sp))
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

@Composable private fun Feedback(state: AuthUiState) {
    val message = state.error ?: state.notice ?: return
    val feedbackBackground = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val feedbackContent = if (state.error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(feedbackBackground).padding(14.dp)) {
        Text(if (state.error != null) "!" else "i", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = feedbackContent)
        Spacer(Modifier.width(10.dp))
        Text(message, fontSize = 12.sp, lineHeight = 18.sp, color = feedbackContent,
            modifier = Modifier.testTag(if (state.error != null) "errorMessage" else "noticeMessage"))
    }
}

@Composable private fun PrimaryButton(label: String, tag: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = .2.sp)
    }
}
