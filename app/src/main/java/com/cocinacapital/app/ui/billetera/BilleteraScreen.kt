package com.cocinacapital.app.ui.billetera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.BeneficioUsuario
import com.cocinacapital.app.data.model.CodigoBeneficio
import com.cocinacapital.app.data.model.EstadoBeneficio
import com.cocinacapital.app.util.fechaHoraChile

/**
 * Billetera del consumidor (HU-14 y HU-16): beneficios vigentes con su código y QR para el local,
 * e historial de canjeados y vencidos. Los datos vienen de `mis_beneficios()` en Supabase.
 */
@Composable
fun BilleteraScreen(
    onExplorarPromociones: () -> Unit = {},
    viewModel: BilleteraViewModel = viewModel(factory = BilleteraViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pestana by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.mensajes.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    // Al volver a la pestaña: el local pudo canjear un beneficio o una promoción pudo vencer.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refrescar() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                    Text(
                        text = stringResource(R.string.billetera_titulo),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.billetera_subtitulo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (val actual = state) {
                    BilleteraUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is BilleteraUiState.Error -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(actual.mensajeRes), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::cargar) { Text(stringResource(R.string.billetera_reintentar)) }
                    }
                    is BilleteraUiState.Success -> if (actual.beneficios.isEmpty()) {
                        BilleteraVacia(onExplorarPromociones)
                    } else {
                        val ahora = viewModel.ahora
                        val vigentes = actual.vigentes(ahora)
                        val historial = actual.historial(ahora)
                        PrimaryTabRow(selectedTabIndex = pestana) {
                            Tab(
                                selected = pestana == 0,
                                onClick = { pestana = 0 },
                                text = { Text(stringResource(R.string.billetera_tab_vigentes, vigentes.size)) }
                            )
                            Tab(
                                selected = pestana == 1,
                                onClick = { pestana = 1 },
                                text = { Text(stringResource(R.string.billetera_tab_historial, historial.size)) }
                            )
                        }
                        val lista = if (pestana == 0) vigentes else historial
                        if (lista.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(if (pestana == 0) R.string.billetera_sin_vigentes else R.string.billetera_sin_historial),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(lista, key = { it.id }) { beneficio ->
                                    TarjetaBeneficio(
                                        beneficio = beneficio,
                                        estado = beneficio.estadoEn(ahora),
                                        quitando = actual.quitandoId == beneficio.id,
                                        ocupado = actual.quitandoId != null,
                                        onMostrarCodigo = { viewModel.onMostrarCodigo(beneficio) },
                                        onQuitar = { viewModel.onPedirQuitar(beneficio) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    (state as? BilleteraUiState.Success)?.let { actual ->
        actual.mostrando?.let { beneficio ->
            CodigoBeneficioDialog(
                titulo = beneficio.titulo,
                localNombre = beneficio.localNombre,
                codigo = beneficio.codigo,
                onCerrar = viewModel::onCerrarCodigo
            )
        }
        actual.porQuitar?.let { beneficio ->
            AlertDialog(
                onDismissRequest = viewModel::onCancelarQuitar,
                title = { Text(stringResource(R.string.billetera_quitar_titulo)) },
                text = { Text(stringResource(R.string.billetera_quitar_texto, beneficio.titulo)) },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmarQuitar) { Text(stringResource(R.string.billetera_quitar_confirmar)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onCancelarQuitar) { Text(stringResource(R.string.billetera_cancelar)) }
                }
            )
        }
    }
}

@Composable
private fun BilleteraVacia(onExplorarPromociones: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.billetera_vacia_titulo),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.billetera_vacia_texto),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExplorarPromociones, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.billetera_explorar))
        }
    }
}

@Composable
private fun TarjetaBeneficio(
    beneficio: BeneficioUsuario,
    estado: EstadoBeneficio,
    quitando: Boolean,
    ocupado: Boolean,
    onMostrarCodigo: () -> Unit,
    onQuitar: () -> Unit
) {
    val vigente = estado == EstadoBeneficio.VIGENTE
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = beneficio.titulo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                EstadoChip(estado)
            }
            Dato(Icons.Outlined.Storefront, beneficio.localNombre)
            if (beneficio.descuentoPorcentaje > 0) {
                Dato(Icons.Outlined.LocalOffer, stringResource(R.string.billetera_descuento, beneficio.descuentoPorcentaje), destacado = true)
            }
            val fecha = when (estado) {
                EstadoBeneficio.VIGENTE -> fechaHoraChile(beneficio.vigenteHasta)?.let { stringResource(R.string.billetera_valido_hasta, it) }
                EstadoBeneficio.CANJEADO -> fechaHoraChile(beneficio.canjeadoEn)?.let { stringResource(R.string.billetera_canjeado_el, it) }
                EstadoBeneficio.VENCIDO -> fechaHoraChile(beneficio.vigenteHasta)?.let { stringResource(R.string.billetera_vencio_el, it) }
            }
            fecha?.let { Dato(Icons.Outlined.CalendarToday, it) }
            Text(
                stringResource(R.string.billetera_codigo_texto, CodigoBeneficio.formatear(beneficio.codigo)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (vigente) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onMostrarCodigo, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.QrCode2, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.billetera_mostrar_codigo))
                    }
                    OutlinedButton(onClick = onQuitar, enabled = !ocupado) {
                        if (quitando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.billetera_quitar))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dato(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, destacado: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = texto,
            style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacado) FontWeight.SemiBold else null,
            color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EstadoChip(estado: EstadoBeneficio) {
    val (texto, color) = when (estado) {
        EstadoBeneficio.VIGENTE -> R.string.billetera_estado_vigente to MaterialTheme.colorScheme.primary
        EstadoBeneficio.CANJEADO -> R.string.billetera_estado_canjeado to MaterialTheme.colorScheme.tertiary
        EstadoBeneficio.VENCIDO -> R.string.billetera_estado_vencido to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(texto)) },
        colors = AssistChipDefaults.assistChipColors(disabledLabelColor = color)
    )
}
