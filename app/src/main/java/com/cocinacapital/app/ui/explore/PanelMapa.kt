package com.cocinacapital.app.ui.explore

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.cocinacapital.app.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class PosicionPanelMapa { MINIMO, MEDIO, AMPLIO }

/** Tres alturas reales; la lista solo recibe el espacio que queda visible sobre el mapa. */
@Composable internal fun PanelMapa(
    state: AnchoredDraggableState<PosicionPanelMapa>, altura: Dp, minimo: Dp,
    titulo: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val total = with(density) { altura.toPx() }
    val minHeight = with(density) { minimo.toPx() }.coerceAtMost(total * .4f)
    val anchors = remember(total, minHeight) { DraggableAnchors {
        PosicionPanelMapa.MINIMO at total - minHeight
        PosicionPanelMapa.MEDIO at total * .5f
        PosicionPanelMapa.AMPLIO at total * .18f
    } }
    val nested = remember(state) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y < 0) Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y < 0 && state.offset > state.anchors.positionOf(PosicionPanelMapa.AMPLIO) + 1f) {
                if (available.y < -600f) state.animateTo(PosicionPanelMapa.AMPLIO, tween(240)) else state.settle(tween(240))
                return Velocity(0f, available.y)
            }
            return Velocity.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            state.settle(tween(240))
            return Velocity(0f, available.y)
        }
    } }
    val estado = stringResource(when (state.settledValue) {
        PosicionPanelMapa.MINIMO -> R.string.ex_panel_minimo
        PosicionPanelMapa.MEDIO -> R.string.ex_panel_medio
        PosicionPanelMapa.AMPLIO -> R.string.ex_panel_amplio
    })
    val minimizar = stringResource(R.string.ex_panel_minimizar)
    val medio = stringResource(R.string.ex_panel_media_pantalla)
    val ampliar = stringResource(R.string.ex_panel_ampliar)
    val siguiente = when (state.targetValue) {
        PosicionPanelMapa.MINIMO -> PosicionPanelMapa.MEDIO
        PosicionPanelMapa.MEDIO -> PosicionPanelMapa.AMPLIO
        PosicionPanelMapa.AMPLIO -> PosicionPanelMapa.MINIMO
    }
    Surface(modifier.fillMaxWidth()
        .offset { IntOffset(0, state.offset.takeUnless { it.isNaN() }?.roundToInt() ?: anchors.positionOf(state.currentValue).roundToInt()) }
        .layout { measurable, constraints ->
            state.updateAnchors(anchors, state.targetValue)
            val visible = (total - state.requireOffset()).roundToInt().coerceIn(0, constraints.maxHeight)
            val placeable = measurable.measure(constraints.copy(minHeight = visible, maxHeight = visible))
            layout(placeable.width, visible) { placeable.placeRelative(0, 0) }
        }
        .nestedScroll(nested)
        .anchoredDraggable(state, Orientation.Vertical),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp) {
        Column {
            Column(Modifier.fillMaxWidth().semantics {
                stateDescription = estado
                customActions = listOf(
                    CustomAccessibilityAction(minimizar) { scope.launch { state.animateTo(PosicionPanelMapa.MINIMO, tween(240)) }; true },
                    CustomAccessibilityAction(medio) { scope.launch { state.animateTo(PosicionPanelMapa.MEDIO, tween(240)) }; true },
                    CustomAccessibilityAction(ampliar) { scope.launch { state.animateTo(PosicionPanelMapa.AMPLIO, tween(240)) }; true }
                )
            }) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Surface(Modifier.size(32.dp, 4.dp), shape = RoundedCornerShape(2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) { }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.targetValue != PosicionPanelMapa.MINIMO) IconButton(onClick = { scope.launch { state.animateTo(PosicionPanelMapa.MINIMO, tween(240)) } }) {
                        Icon(Icons.Outlined.CloseFullscreen, minimizar)
                    }
                    IconButton(onClick = { scope.launch { state.animateTo(siguiente, tween(240)) } }) {
                        Icon(if (siguiente == PosicionPanelMapa.MINIMO) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            when (siguiente) { PosicionPanelMapa.MINIMO -> minimizar; PosicionPanelMapa.MEDIO -> medio; PosicionPanelMapa.AMPLIO -> ampliar })
                    }
                }
            }
            content()
        }
    }
}
