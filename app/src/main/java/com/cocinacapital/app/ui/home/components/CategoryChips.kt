package com.cocinacapital.app.ui.home.components

import androidx.compose.ui.res.stringResource
import com.cocinacapital.app.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cocinacapital.app.data.model.Categoria

/** Fila horizontal deslizable de chips de categorías. El primer chip es "Todos". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    categorias: List<Categoria>,
    seleccionadaId: String?,
    onCategoriaClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "todos") {
            FilterChip(
                selected = seleccionadaId == null,
                onClick = { onCategoriaClick(null) },
                label = { Text(stringResource(R.string.ex_todos)) }
            )
        }
        items(categorias, key = { it.id }) { categoria ->
            FilterChip(
                selected = categoria.id == seleccionadaId,
                onClick = { onCategoriaClick(categoria.id) },
                label = { Text(categoria.nombre) }
            )
        }
    }
}
