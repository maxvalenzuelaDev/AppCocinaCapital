package com.cocinacapital.app.ui.explore

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.util.normalizado
import java.util.Locale
import java.text.NumberFormat

@Composable internal fun MapLauncher(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.primary
    val street = MaterialTheme.colorScheme.outlineVariant
    val label = stringResource(R.string.ex_abrir_mapa)
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, street.copy(alpha = .4f)),
        // Mínimo 60 dp; con la fuente grande se ensancha para que "Mapa" no se corte (T-18).
        modifier = Modifier.defaultMinSize(60.dp, 60.dp).semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.matchParentSize().padding(bottom = 22.dp)) {
                for (fraction in listOf(.22f, .53f, .84f)) drawLine(street, Offset(size.width * fraction, 0f), Offset(size.width * (fraction - .1f), size.height), 3.dp.toPx())
                drawLine(street, Offset(0f, size.height * .40f), Offset(size.width, size.height * .65f), 3.dp.toPx())
                drawCircle(ink, 4.dp.toPx(), Offset(size.width * .55f, size.height * .48f))
            }
            Text(stringResource(R.string.ex_mapa), Modifier.padding(start = 8.dp, end = 8.dp, top = 30.dp, bottom = 5.dp),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable internal fun ExploreCategory(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .15f) else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.selectable(selected, role = Role.Tab, onClick = onClick)) {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable internal fun SearchBox(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null, onActivate: (() -> Unit)? = null) {
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    Box(modifier) {
    OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().then(if (onActivate != null)
        Modifier.focusProperties { canFocus = false }.clearAndSetSemantics { } else Modifier), singleLine = true, readOnly = onActivate != null,
        placeholder = { Text(stringResource(R.string.ex_buscar_locales), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { if (onBack == null) Icon(Icons.Outlined.Search, null)
            else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ex_volver_explorar)) } },
        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.ex_borrar_busqueda)) } }) else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { foco.clearFocus(); teclado?.hide() }))
    if (onActivate != null) {
        val etiqueta = stringResource(R.string.home_abrir_busqueda)
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onActivate)
            .semantics { contentDescription = etiqueta })
    }
    }
}

@Composable internal fun RadiusMenu(radius: Int, onRadius: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.ex_km, radius)); Icon(Icons.Outlined.ExpandMore, null) }
        DropdownMenu(expanded, { expanded = false }) {
            listOf(5, 10, 20).forEach { km -> DropdownMenuItem(text = { Text(stringResource(R.string.ex_hasta_km, km)) }, onClick = { onRadius(km); expanded = false }) }
        }
    }
}

@Composable internal fun localCount(count: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.ex_cantidad, count, count)
@Composable internal fun distanceLabel(local: LocalGastronomico) = local.distanciaKm?.let {
    stringResource(R.string.ex_distancia, String.format(Locale.forLanguageTag("es-CL"), "%.1f", it))
} ?: stringResource(R.string.ex_sin_distancia)
internal fun rating(local: LocalGastronomico) = String.format(Locale.forLanguageTag("es-CL"), "%.1f", local.valoracion)

@Composable internal fun preciosLabel(local: LocalGastronomico): String {
    val promedio = local.ticketPromedioPersona?.takeIf { it > 0 } ?: return stringResource(R.string.ex_consumo_no_informado)
    return stringResource(R.string.ex_consumo_persona, monedaCL(promedio))
}

internal fun monedaCL(monto: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL"))
    .apply { maximumFractionDigits = 0 }.format(monto)

@Composable internal fun LocalRow(local: LocalGastronomico, compacto: Boolean = false, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
                if (local.imagenUrl.isNullOrBlank()) {
                    val categoria = local.categoriaNombre.normalizado()
                    val icono = when {
                        "burger" in categoria -> Icons.Outlined.LunchDining
                        "cafe" in categoria -> Icons.Outlined.LocalCafe
                        "marina" in categoria -> Icons.Outlined.SetMeal
                        "sushi" in categoria -> Icons.Outlined.RamenDining
                        else -> Icons.Outlined.Restaurant
                    }
                    Icon(icono, null, Modifier.padding(13.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                else AsyncImage(model = local.imagenUrl, contentDescription = null, contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f)) {
                Text(local.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ex_categoria_comuna, local.categoriaNombre, local.comuna), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                val distancia = if (compacto && local.distanciaKm != null) stringResource(R.string.ex_km_decimal,
                    String.format(Locale.forLanguageTag("es-CL"), "%.1f", local.distanciaKm)) else distanceLabel(local)
                Text(stringResource(R.string.ex_valoracion_distancia, rating(local), distancia), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!compacto || local.ticketPromedioPersona?.let { it > 0 } == true)
                    Text(preciosLabel(local), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.ex_ver_local))
        }
    }
}
