package com.cocinacapital.app.ui.oferente

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.CodigoBeneficio
import com.cocinacapital.app.data.model.EstadoCanje
import com.cocinacapital.app.data.model.ResultadoCanje
import com.cocinacapital.app.util.fechaHoraChile

/**
 * Pestaña Canjear del oferente (HU-15 y HU-29): escanea el QR del cliente con la cámara o escribe
 * su código, revisa el estado del beneficio y confirma el canje.
 */
@Composable
fun CanjeScreen(
    usuarioId: String,
    viewModel: CanjeViewModel = viewModel(key = "canje_$usuarioId", factory = CanjeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        viewModel.onFotoTomada(exito)
    }
    // La app declara el permiso de cámara (foto de perfil), así que Android lo exige también para
    // abrir la app de cámara del teléfono.
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) camara.launch(Uri.parse(viewModel.onEscanear())) else viewModel.onPermisoCamaraDenegado()
    }
    val escanear = {
        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (concedido) camara.launch(Uri.parse(viewModel.onEscanear())) else permisoCamara.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.canje_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = escanear, enabled = !state.ocupado, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.canje_escanear))
        }
        OutlinedTextField(
            value = state.codigo,
            onValueChange = viewModel::onCodigo,
            label = { Text(stringResource(R.string.canje_codigo)) },
            placeholder = { Text(stringResource(R.string.canje_codigo_ejemplo)) },
            singleLine = true,
            isError = state.error != null && state.resultado == null,
            supportingText = state.error?.let { { Text(stringResource(it)) } },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onRevisar() }),
            enabled = !state.ocupado,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = viewModel::onRevisar,
            enabled = !state.ocupado && state.codigo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.ocupado) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.canje_revisar))
            }
        }
        state.resultado?.let { resultado ->
            TarjetaResultado(resultado)
            when {
                resultado.estado == EstadoCanje.VALIDO -> Button(
                    onClick = viewModel::onConfirmar,
                    enabled = !state.ocupado,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.canje_confirmar)) }
                else -> OutlinedButton(onClick = viewModel::onNuevo, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.canje_nuevo))
                }
            }
        }
    }
}

@Composable
private fun TarjetaResultado(resultado: ResultadoCanje) {
    val positivo = resultado.estado == EstadoCanje.VALIDO || resultado.canjeadoAhora
    val estado = when {
        resultado.canjeadoAhora -> R.string.canje_estado_canjeado_ahora
        else -> when (resultado.estado) {
            EstadoCanje.VALIDO -> R.string.canje_estado_valido
            EstadoCanje.CANJEADO -> R.string.canje_estado_canjeado
            EstadoCanje.VENCIDO -> R.string.canje_estado_vencido
            EstadoCanje.NO_DISPONIBLE -> R.string.canje_estado_no_disponible
            EstadoCanje.NO_INICIADO -> R.string.canje_estado_no_iniciado
            EstadoCanje.FUERA_DE_HORARIO -> R.string.canje_estado_fuera_de_horario
        }
    }
    val color = if (positivo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (positivo) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = color
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(estado), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
            Text(resultado.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${resultado.localNombre} · ${stringResource(R.string.billetera_descuento, resultado.descuentoPorcentaje)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(CodigoBeneficio.formatear(resultado.codigo), fontFamily = FontFamily.Monospace)
            if (resultado.condiciones.isNotBlank()) {
                Text(stringResource(R.string.canje_condiciones, resultado.condiciones), style = MaterialTheme.typography.bodySmall)
            }
            val fecha = if (resultado.estado == EstadoCanje.CANJEADO) {
                fechaHoraChile(resultado.canjeadoEn)?.let { stringResource(R.string.canje_canjeado_el, it) }
            } else {
                fechaHoraChile(resultado.vigenteHasta)?.let { stringResource(R.string.canje_vigente_hasta, it) }
            }
            fecha?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
