package com.cocinacapital.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.R
import com.cocinacapital.app.ui.explore.SearchBox
import com.cocinacapital.app.ui.explore.ExploreFilterButton

/**
 * Encabezado: nombre de la app + comuna actual del usuario. No usa TopAppBar porque su alto es fijo
 * (64 dp) y con la fuente grande del sistema cortaba la ubicación (T-18).
 */
@Composable
fun HomeTopBar(
    ubicacion: String,
    onLocationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
                Text(
                    text = stringResource(R.string.home_nombre),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                // Zona táctil de al menos 48 dp para pedir la ubicación.
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onLocationClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null, // decorativo: el texto ya dice la ubicación
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = ubicacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
    }
}

/** Acceso a la búsqueda completa: no abre un segundo formulario dentro de Inicio. */
@Composable
fun SearchField(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevada: Boolean = false
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchBox("", {}, Modifier.weight(1f), onActivate = onSearchClick, onBack = if (elevada) onSearchClick else null)
        ExploreFilterButton(0, onSearchClick)
    }
}
