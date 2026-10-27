package com.cocinacapital.app.ui.oferente

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.LocalGastronomico

/**
 * Local con el que trabajan Catálogo y Promociones. Con un solo local solo lo muestra; con varios,
 * al tocarlo se elige otro de la lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorLocal(
    locales: List<LocalGastronomico>,
    seleccionado: LocalGastronomico?,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by remember { mutableStateOf(false) }
    val variosLocales = locales.size > 1
    Box(modifier) {
        OutlinedCard(onClick = { abierto = true }, enabled = variosLocales, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storefront, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.selector_local_etiqueta),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        seleccionado?.nombre.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (variosLocales) ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto)
            }
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            locales.forEach { local ->
                DropdownMenuItem(
                    text = { Text(local.nombre) },
                    trailingIcon = { if (local.id == seleccionado?.id) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { onElegir(local.id); abierto = false }
                )
            }
        }
    }
}
