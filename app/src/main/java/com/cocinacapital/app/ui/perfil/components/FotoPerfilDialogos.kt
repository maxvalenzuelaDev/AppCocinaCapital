package com.cocinacapital.app.ui.perfil.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R
import com.cocinacapital.app.ui.perfil.DialogoFoto

/** Diálogos del flujo de foto de perfil. Qué diálogo se muestra lo decide el ViewModel. */
@Composable
fun FotoPerfilDialogos(
    dialogo: DialogoFoto?,
    tieneFoto: Boolean,
    onTomarFoto: () -> Unit,
    onElegirGaleria: () -> Unit,
    onQuitarFoto: () -> Unit,
    onAceptarExplicacion: () -> Unit,
    onAbrirAjustes: () -> Unit,
    onCerrar: () -> Unit
) {
    when (dialogo) {
        null -> Unit

        DialogoFoto.ELEGIR_ORIGEN -> AlertDialog(
            onDismissRequest = onCerrar,
            title = { Text(stringResource(R.string.perfil_foto_titulo)) },
            text = {
                Column {
                    OpcionFoto(Icons.Outlined.PhotoCamera, stringResource(R.string.perfil_foto_tomar), onTomarFoto)
                    OpcionFoto(Icons.Outlined.PhotoLibrary, stringResource(R.string.perfil_foto_galeria), onElegirGaleria)
                    if (tieneFoto) {
                        OpcionFoto(
                            Icons.Outlined.Delete,
                            stringResource(R.string.perfil_foto_quitar),
                            onQuitarFoto,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.perfil_cancelar)) } }
        )

        DialogoFoto.EXPLICAR_PERMISO -> AlertDialog(
            onDismissRequest = onCerrar,
            icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
            title = { Text(stringResource(R.string.perfil_permiso_camara_titulo)) },
            text = { Text(stringResource(R.string.perfil_permiso_camara_explicacion)) },
            confirmButton = { TextButton(onClick = onAceptarExplicacion) { Text(stringResource(R.string.perfil_continuar)) } },
            dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.perfil_ahora_no)) } }
        )

        DialogoFoto.PERMISO_DENEGADO -> AlertDialog(
            onDismissRequest = onCerrar,
            title = { Text(stringResource(R.string.perfil_permiso_camara_denegado_titulo)) },
            text = { Text(stringResource(R.string.perfil_permiso_camara_denegado)) },
            confirmButton = { TextButton(onClick = onAbrirAjustes) { Text(stringResource(R.string.perfil_abrir_ajustes)) } },
            dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.perfil_cancelar)) } }
        )

        DialogoFoto.ERROR_GUARDAR -> AlertDialog(
            onDismissRequest = onCerrar,
            title = { Text(stringResource(R.string.perfil_foto_error_titulo)) },
            text = { Text(stringResource(R.string.perfil_foto_error)) },
            confirmButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.perfil_entendido)) } }
        )
    }
}

@Composable
private fun OpcionFoto(
    icono: ImageVector,
    texto: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
        Spacer(Modifier.width(16.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
