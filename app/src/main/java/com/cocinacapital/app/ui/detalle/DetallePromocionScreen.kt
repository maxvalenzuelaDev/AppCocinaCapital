package com.cocinacapital.app.ui.detalle

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.EstadoVigenciaPromocion
import com.cocinacapital.app.data.model.CuposPromocion
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.model.cuposParaDetalle
import com.cocinacapital.app.data.model.franjaValida
import com.cocinacapital.app.data.model.franjaParaDetalle
import com.cocinacapital.app.data.model.diasParaDetalle
import com.cocinacapital.app.data.model.horarioPorConfirmar
import com.cocinacapital.app.ui.detalle.components.DetalleTopBar
import com.cocinacapital.app.util.LocaleCL
import java.time.Instant
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.cocinacapital.app.ui.billetera.GuardarPromocionButton

@Composable
fun DetallePromocionScreen(
    promocionId: String,
    onVolver: () -> Unit,
    onLocalClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    model: DetallePromocionViewModel = viewModel(
        factory = DetallePromocionViewModel.factory(promocionId)
    )
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(model, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                model.actualizarVigencia()
                delay(30_000)
            }
        }
    }
    DetallePromocionContent(state, onVolver, onLocalClick, model::onReintentar, modifier)
}

@Composable
fun DetallePromocionContent(
    state: DetallePromocionUiState,
    onVolver: () -> Unit,
    onLocalClick: (String) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,

        bottomBar = {
            val detalle = state as? DetallePromocionUiState.Success

            if (detalle != null) {
                Surface(tonalElevation = 2.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 12.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        // Botón de nuestra billetera.
                        GuardarPromocionButton(
                            promocionId = detalle.promocion.id
                        )

                        // Botón Ver local de nuestros compañeros.
                        detalle.local?.let { local ->
                            Button(
                                onClick = { onLocalClick(local.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(
                                        R.string.detalle_promocion_ver_local
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            DetalleTopBar(
                titulo = stringResource(R.string.detalle_promocion_barra_titulo),
                onVolver = onVolver,
                acciones = {
                    IconButton(onClick = onReintentar, enabled = state !is DetallePromocionUiState.Loading) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.detalle_promocion_actualizar))
                    }
                }
            )
        }
    ) { padding ->
        val contenido = Modifier.fillMaxSize().padding(padding)
        when (state) {
            DetallePromocionUiState.Loading -> Column(
                contenido,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.detalle_promocion_cargando))
            }
            DetallePromocionUiState.Error -> MensajePromocion(
                R.string.detalle_promocion_error, Icons.Outlined.CloudOff, contenido, onReintentar
            )
            DetallePromocionUiState.NoEncontrada -> MensajePromocion(
                R.string.detalle_promocion_no_encontrada, Icons.Outlined.LocalOffer, contenido
            )
            is DetallePromocionUiState.Success -> PromocionContent(
                state, onReintentar, contenido
            )
        }
    }
}

@Composable
private fun MensajePromocion(
    @StringRes mensaje: Int,
    icono: ImageVector,
    modifier: Modifier,
    onReintentar: (() -> Unit)? = null
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(mensaje), textAlign = TextAlign.Center)
        if (onReintentar != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReintentar) { Text(stringResource(R.string.detalle_reintentar)) }
        }
    }
}

@Composable
private fun PromocionContent(
    state: DetallePromocionUiState.Success,
    onReintentar: () -> Unit,
    modifier: Modifier
) {
    val promocion = state.promocion
    val local = state.local
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "cabecera") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                local?.let {
                    Text(
                        it.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    promocion.titulo,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (promocion.descuentoPorcentaje in 1..100) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.detalle_promocion_descuento, promocion.descuentoPorcentaje),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(stringResource(estadoTexto(state.vigencia.estado)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                } else {
                    Text(stringResource(estadoTexto(state.vigencia.estado)),
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(cuposTexto(promocion, state.cuposConfigurados),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                promocion.descripcion.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item(key = "condiciones") {
            if (promocion.condiciones.isNotBlank()) {
                SeccionPromocion(R.string.detalle_promocion_condiciones, Icons.Outlined.Description) {
                    Text(promocion.condiciones)
                }
            } else {
                Text(stringResource(R.string.detalle_promocion_condiciones_pendientes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "vigencia") {
            SeccionPromocion(R.string.detalle_promocion_cuando, Icons.Outlined.Schedule) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.detalle_promocion_desde, fechaTexto(state.vigencia.desde)),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.detalle_promocion_hasta, fechaTexto(state.vigencia.hasta)),
                        style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(diasTexto(promocion, state.horarioConfigurado),
                    style = MaterialTheme.typography.bodyMedium)
                val franja = promocion.franjaParaDetalle(state.horarioConfigurado)
                Text(when {
                    franja != null -> stringResource(R.string.detalle_promocion_franja,
                        franja.desde.format(formatoHoraPromocion), franja.hasta.format(formatoHoraPromocion))
                    state.horarioConfigurado && !promocion.horarioPorConfirmar(true) && promocion.horaInicio == null && promocion.horaFin == null ->
                        stringResource(R.string.detalle_promocion_horario_del_local)
                    else -> stringResource(R.string.detalle_promocion_horario_pendiente)
                }, style = MaterialTheme.typography.bodyMedium)
                if (promocion.franjaValida() == null && franja != null ||
                    promocion.diasSemana == null && promocion.diasParaDetalle(state.horarioConfigurado) != null) {
                    Text(stringResource(R.string.detalle_promocion_horario_descripcion),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.detalle_promocion_zona_horaria),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "local") {
            SeccionPromocion(R.string.detalle_promocion_local, Icons.Outlined.Storefront) {
                if (local != null) {
                    local.imagenUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        val fondo = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest)
                        AsyncImage(
                            model = url,
                            contentDescription = stringResource(R.string.detalle_local_foto, local.nombre),
                            contentScale = ContentScale.Crop,
                            placeholder = fondo,
                            error = fondo,
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                    Text(local.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_categoria_comuna, local.categoriaNombre, local.comuna),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(local.direccion?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detalle_promocion_direccion_pendiente))
                    local.horario?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.detalle_promocion_horario_local, it),
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(stringResource(if (state.errorLocal) R.string.detalle_promocion_error_local
                        else R.string.detalle_promocion_local_no_disponible))
                    if (state.errorLocal) TextButton(onClick = onReintentar) {
                        Text(stringResource(R.string.detalle_reintentar))
                    }
                }
            }
        }
    }
}

@Composable
private fun cuposTexto(promocion: Promocion, camposPresentes: Boolean): String =
    when (val cupos = promocion.cuposParaDetalle(camposPresentes)) {
        CuposPromocion.SinInformar -> stringResource(R.string.detalle_promocion_cupos_pendientes)
        CuposPromocion.SinLimite -> stringResource(R.string.detalle_promocion_cupos_sin_limite)
        is CuposPromocion.Disponibles -> if (cupos.cantidad == 0) {
            stringResource(R.string.detalle_promocion_sin_cupos)
        } else {
            pluralStringResource(R.plurals.detalle_promocion_cantidad_cupos, cupos.cantidad,
                String.format(LocaleCL, "%,d", cupos.cantidad))
        }
    }

@Composable
private fun SeccionPromocion(
    @StringRes titulo: Int,
    icono: ImageVector,
    contenido: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(titulo), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            contenido()
        }
    }
}

@StringRes
private fun estadoTexto(estado: EstadoVigenciaPromocion): Int = when (estado) {
    EstadoVigenciaPromocion.SIN_CUPOS -> R.string.detalle_promocion_sin_cupos
    EstadoVigenciaPromocion.FUERA_DE_HORARIO -> R.string.detalle_promocion_fuera_horario
    EstadoVigenciaPromocion.VIGENTE -> R.string.detalle_promocion_vigente
    EstadoVigenciaPromocion.PROGRAMADA -> R.string.detalle_promocion_programada
    EstadoVigenciaPromocion.FINALIZADA -> R.string.detalle_promocion_finalizada
    EstadoVigenciaPromocion.NO_DISPONIBLE -> R.string.detalle_promocion_no_disponible
    EstadoVigenciaPromocion.POR_CONFIRMAR -> R.string.detalle_promocion_vigencia_pendiente
}

private val formatoFechaPromocion = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", LocaleCL)
    .withZone(ZoneId.of("America/Santiago"))

@Composable
private fun fechaTexto(instante: Instant?): String =
    instante?.let(formatoFechaPromocion::format)
        ?: stringResource(R.string.detalle_promocion_fecha_pendiente)

private val formatoHoraPromocion = DateTimeFormatter.ofPattern("HH:mm", LocaleCL)

@Composable
private fun diasTexto(promocion: Promocion, camposPresentes: Boolean): String {
    val dias = promocion.diasParaDetalle(camposPresentes)
    if (dias == null) return stringResource(if (camposPresentes && !promocion.horarioPorConfirmar(true)) R.string.detalle_promocion_todos_dias
        else R.string.detalle_promocion_dias_pendientes)
    if (dias.isEmpty() || dias.any { it !in 1..7 }) return stringResource(R.string.detalle_promocion_dias_pendientes)
    val nombres = dias.distinct().sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.FULL, LocaleCL)
    }
    return stringResource(R.string.detalle_promocion_dias, nombres)
}
