package com.cocinacapital.app.ui.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.location.LocationProblem
import kotlinx.coroutines.flow.collectLatest

data class AccionUbicacion(val cargando: Boolean, val solicitar: () -> Unit)

/** Consulta compartida desde cualquier botón, sin una pantalla adicional. */
@Composable
fun rememberUbicacionAction(snackbar: SnackbarHostState,
    model: UbicacionViewModel = viewModel(factory = UbicacionViewModel.Factory)): AccionUbicacion {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state by model.uiState.collectAsStateWithLifecycle()
    var explicarPermiso by rememberSaveable { mutableStateOf(false) }
    var volverDeAjustes by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.tieneUbicacion()) model.obtener() else model.permisoDenegado()
    }

    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.validar()
                if (volverDeAjustes) {
                    volverDeAjustes = false
                    if (context.tieneUbicacion()) model.obtener() else model.permisoDenegado()
                }
            }
            if (event == Lifecycle.Event.ON_STOP) model.cancelar()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.cancelar() }
    }

    LaunchedEffect(model, snackbar) {
        model.resultados.collectLatest { result ->
            val problem = (result as? UbicacionUiState.Error)?.problema
            val message = when (problem) {
                LocationProblem.PERMISSION_REQUIRED -> R.string.gps_sin_permiso
                LocationProblem.LOCATION_DISABLED -> R.string.gps_apagado
                LocationProblem.TIMEOUT -> R.string.gps_tiempo
                LocationProblem.NO_PROVIDER -> R.string.gps_sin_proveedor
                null -> if (result is UbicacionUiState.Success) R.string.gps_actualizada else R.string.gps_error
                else -> R.string.gps_error
            }
            val activity = context.activity()
            val bloqueado = problem == LocationProblem.PERMISSION_REQUIRED && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            val action = when {
                problem == LocationProblem.LOCATION_DISABLED -> R.string.gps_activar
                bloqueado -> R.string.gps_ajustes
                else -> null
            }
            val response = snackbar.showSnackbar(
                message = context.getString(message), actionLabel = action?.let(context::getString),
                duration = if (problem == null) SnackbarDuration.Short else SnackbarDuration.Long
            )
            if (response == SnackbarResult.ActionPerformed) {
                volverDeAjustes = true
                val intent = if (bloqueado) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
    if (explicarPermiso) AlertDialog(
        onDismissRequest = { explicarPermiso = false },
        title = { Text(stringResource(R.string.gps_permiso_titulo)) },
        text = { Text(stringResource(R.string.gps_permiso_explicacion)) },
        confirmButton = { TextButton(onClick = {
            explicarPermiso = false
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }) { Text(stringResource(R.string.gps_permiso_continuar)) } },
        dismissButton = { TextButton(onClick = { explicarPermiso = false }) {
            Text(stringResource(R.string.gps_permiso_ahora_no))
        } }
    )
    return AccionUbicacion(state is UbicacionUiState.Loading) {
        if (model.uiState.value !is UbicacionUiState.Loading) {
            snackbar.currentSnackbarData?.dismiss()
            if (context.tieneUbicacion()) model.obtener()
            else explicarPermiso = true
        }
    }
}

private fun Context.tieneUbicacion() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
