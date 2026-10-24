package com.cocinacapital.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.ui.home.components.CategoryChips
import com.cocinacapital.app.ui.home.components.HomeTopBar
import com.cocinacapital.app.ui.home.components.LocalCard
import com.cocinacapital.app.ui.home.components.PromocionCard
import com.cocinacapital.app.ui.home.components.SearchField

/**
 * Pantalla Home "con estado": conecta el ViewModel con la UI.
 * La UI real vive en [HomeContent], que no conoce el ViewModel (más fácil de previsualizar y probar).
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLocalClick: (String) -> Unit = {},
    onPromocionClick: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val ubicacion by viewModel.ubicacion.collectAsStateWithLifecycle()

    HomeContent(
        uiState = uiState,
        query = query,
        ubicacion = ubicacion.comuna,
        onQueryChange = viewModel::onSearchQueryChange,
        onCategoriaClick = viewModel::onCategoriaClick,
        onReintentar = viewModel::onReintentar,
        onLocalClick = onLocalClick,
        onPromocionClick = onPromocionClick,
        modifier = modifier
    )
}

/** Pantalla Home "sin estado" (stateless). */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    query: String,
    ubicacion: String,
    onQueryChange: (String) -> Unit,
    onCategoriaClick: (String?) -> Unit,
    onReintentar: () -> Unit,
    onLocalClick: (String) -> Unit,
    onPromocionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // El encabezado y la búsqueda se muestran SIEMPRE, sea cual sea el estado.
        HomeTopBar(ubicacion = ubicacion)
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Fundido al pasar de cargando a contenido o error (T-24). La clave es el tipo de estado:
        // filtrar o buscar actualiza el Success sin repetir el fundido (eso lo anima animateItem).
        AnimatedContent(
            targetState = uiState,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "estadoHome"
        ) { state ->
            when (state) {
                HomeUiState.Loading -> LoadingContent()
                is HomeUiState.Error -> ErrorContent(message = state.message, onReintentar = onReintentar)
                is HomeUiState.Success -> SuccessContent(
                    state = state,
                    onCategoriaClick = onCategoriaClick,
                    onLocalClick = onLocalClick,
                    onPromocionClick = onPromocionClick
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Cargando locales y promociones…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorContent(message: String, onReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onReintentar) { Text("Reintentar") }
    }
}

@Composable
private fun SuccessContent(
    state: HomeUiState.Success,
    onCategoriaClick: (String?) -> Unit,
    onLocalClick: (String) -> Unit,
    onPromocionClick: (String) -> Unit
) {
    // Un solo contenedor vertical con scroll; el carrusel horizontal va dentro de un item.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "categorias") {
            CategoryChips(
                categorias = state.categorias,
                seleccionadaId = state.categoriaSeleccionadaId,
                onCategoriaClick = onCategoriaClick
            )
        }

        // ---- Para ti ----
        item(key = "para_ti_titulo") {
            SectionHeader("Para ti", "Locales recomendados según tu perfil y cercanía")
        }
        item(key = "para_ti") {
            if (state.localesParaTi.isEmpty()) {
                EmptyMessage("No encontramos locales con ese criterio.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.localesParaTi, key = { it.id }) { local ->
                        // animateItem: las tarjetas se desplazan y aparecen suavemente al filtrar.
                        LocalCard(
                            local = local,
                            onClick = { onLocalClick(local.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // ---- Promociones vigentes ----
        item(key = "promos_titulo") {
            SectionHeader("Promociones vigentes", "Aprovecha las horas de baja demanda")
        }
        if (state.promociones.isEmpty()) {
            item(key = "promos_vacio") {
                EmptyMessage("No hay promociones vigentes con ese criterio.", Modifier.animateItem())
            }
        } else {
            items(state.promociones, key = { it.id }) { promo ->
                PromocionCard(
                    promocion = promo,
                    onClick = { onPromocionClick(promo.id) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(titulo: String, subtitulo: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyMessage(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
