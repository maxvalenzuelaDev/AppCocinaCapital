package com.cocinacapital.app.ui.perfil.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.PerfilUsuario
import java.io.File

/**
 * Avatar: foto local elegida en el Perfil ([fotoRuta]), si no la foto de la cuenta, y si no las iniciales.
 * Con [onClick] muestra una insignia de cámara y se puede tocar para cambiar la foto.
 */
@Composable
fun Avatar(
    usuario: PerfilUsuario,
    modifier: Modifier = Modifier,
    fotoRuta: String? = null,
    onClick: (() -> Unit)? = null
) {
    val foto: Any? = fotoRuta?.let(::File) ?: usuario.fotoUrl
    val descripcion = stringResource(R.string.perfil_foto_descripcion, usuario.nombre)

    Box(modifier = modifier.size(96.dp)) {
        Surface(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .then(
                    if (onClick != null) Modifier.clickable(
                        onClickLabel = stringResource(R.string.perfil_foto_cambiar),
                        onClick = onClick
                    ) else Modifier
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (foto != null) {
                AsyncImage(
                    model = foto,
                    contentDescription = descripcion,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp)
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        usuario.iniciales,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        if (onClick != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** Contenedor de sección con título. */
@Composable
fun SeccionCard(
    titulo: String,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (descripcion != null) {
            Text(
                descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

/** Fila con interruptor; toda la fila es clickeable (mejor accesibilidad). */
@Composable
fun SwitchRow(
    titulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) {
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun Estadistica(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Separador() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
