package com.cocinacapital.app.ui.oferente

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.util.LocaleCL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Pestaña "Promociones" del oferente (HU-27): lista de sus promociones y formulario para crear una. */
@Composable
fun PromocionesScreen(
    snackbar: SnackbarHostState,
    usuarioId: String,
    onIrAMiLocal: () -> Unit,
    // Una instancia por cuenta, igual que Mi local y Catálogo.
    viewModel: PromocionesViewModel = viewModel(key = "promociones_$usuarioId", factory = PromocionesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.onMostrar() }
    LaunchedEffect(viewModel, snackbar) {
        viewModel.mensajes.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    when (val state = uiState) {
        PromocionesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is PromocionesUiState.Error ->
            MensajePromociones(Icons.Outlined.CloudOff, state.mensajeRes, R.string.mi_local_reintentar, viewModel::onReintentar)

        PromocionesUiState.SinLocal ->
            MensajePromociones(Icons.Outlined.Storefront, R.string.promo_sin_local, R.string.catalogo_ir_mi_local, onIrAMiLocal)

        is PromocionesUiState.Success -> {
            val formulario = state.formulario
            if (formulario == null) {
                ListaPromociones(state.promociones, viewModel.ahora, viewModel::onNueva)
            } else {
                BackHandler(onBack = viewModel::onCerrarFormulario)
                FormularioPromocionView(
                    formulario = formulario,
                    errores = state.errores,
                    guardando = state.guardando,
                    onCambio = viewModel::onCambio,
                    onGuardar = viewModel::onGuardar,
                    onCerrar = viewModel::onCerrarFormulario
                )
            }
        }
    }
}

// ---------------- Lista ----------------

@Composable
private fun ListaPromociones(promociones: List<Promocion>, ahora: Instant, onNueva: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Espacio abajo para que el botón flotante no tape la última promoción.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.promo_ayuda),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (promociones.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.LocalOffer, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.promo_vacio), textAlign = TextAlign.Center)
                    }
                }
            }
            items(promociones, key = { it.id }) { promo -> PromocionItem(promo, promo.estado(ahora)) }
        }
        ExtendedFloatingActionButton(
            onClick = onNueva,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.promo_nueva)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun PromocionItem(promo: Promocion, estado: EstadoPromocion) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    promo.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.home_descuento, promo.descuentoPorcentaje),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(estado.etiquetaRes)) }
            )
            Text(vigenciaTexto(promo), style = MaterialTheme.typography.bodySmall)
            Text(
                horarioTexto(promo.diasSemana, promo.horaInicio, promo.horaFin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when {
                    promo.cuposTotal == null -> stringResource(R.string.promo_sin_limite)
                    else -> stringResource(R.string.promo_cupos_disponibles, promo.cuposDisponibles ?: promo.cuposTotal, promo.cuposTotal)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun vigenciaTexto(promo: Promocion): String {
    val zona = ZoneId.systemDefault()
    fun dia(iso: String?) = iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(zona).toLocalDate() }.getOrNull() }
    val desde = dia(promo.vigenteDesde)
    val hasta = dia(promo.vigenteHasta)
    return if (desde != null && hasta != null) stringResource(R.string.promo_vigencia, fecha(desde), fecha(hasta))
        else stringResource(R.string.home_vigencia_pendiente)
}

@Composable
private fun horarioTexto(dias: List<Int>?, inicio: String?, fin: String?): String {
    val textoDias = if (dias.isNullOrEmpty() || dias.size == 7) stringResource(R.string.promo_todos_los_dias)
        else dias.sorted().joinToString(", ") { nombreDia(DayOfWeek.of(it)) }
    val franja = if (inicio != null && fin != null) " · ${inicio.take(5)}–${fin.take(5)}" else ""
    return textoDias + franja
}

// ---------------- Formulario ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioPromocionView(
    formulario: FormularioPromocion,
    errores: ErroresPromocion,
    guardando: Boolean,
    onCambio: (FormularioPromocion.() -> FormularioPromocion) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit
) {
    var eligiendoFecha by remember { mutableStateOf<Extremo?>(null) }
    var eligiendoHora by remember { mutableStateOf<Extremo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar, enabled = !guardando) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.catalogo_cancelar))
            }
            Text(stringResource(R.string.promo_nueva), style = MaterialTheme.typography.titleMedium)
        }

        CampoPromo(formulario.titulo, { v -> onCambio { copy(titulo = v) } }, R.string.promo_titulo, errores.titulo, R.string.promo_titulo_ayuda)
        CampoPromo(
            formulario.descuento, { v -> onCambio { copy(descuento = v) } }, R.string.promo_descuento, errores.descuento,
            R.string.promo_descuento_ayuda, teclado = KeyboardType.Number
        )
        CampoPromo(formulario.descripcion, { v -> onCambio { copy(descripcion = v) } }, R.string.promo_descripcion, errores.descripcion, null, lineas = 2)
        CampoPromo(
            formulario.condiciones, { v -> onCambio { copy(condiciones = v) } }, R.string.promo_condiciones, errores.condiciones,
            R.string.promo_condiciones_ayuda, lineas = 2
        )

        // Vigencia
        Seccion(stringResource(R.string.promo_vigencia_titulo), errores.vigencia) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonValor(
                    Icons.Outlined.CalendarMonth,
                    stringResource(R.string.promo_desde, formulario.desde?.let { fecha(it) } ?: "—"),
                    Modifier.weight(1f)
                ) { eligiendoFecha = Extremo.INICIO }
                BotonValor(
                    Icons.Outlined.CalendarMonth,
                    stringResource(R.string.promo_hasta, formulario.hasta?.let { fecha(it) } ?: "—"),
                    Modifier.weight(1f)
                ) { eligiendoFecha = Extremo.FIN }
            }
        }

        // Días y franja horaria
        Seccion(stringResource(R.string.promo_dias_titulo), errores.horario) {
            Text(
                stringResource(R.string.promo_dias_ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { dia ->
                    val elegido = dia in formulario.dias
                    FilterChip(
                        selected = elegido,
                        onClick = { onCambio { copy(dias = if (elegido) dias - dia else dias + dia) } },
                        label = { Text(nombreDia(dia)) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.promo_con_horario), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = formulario.conHorario, onCheckedChange = { v -> onCambio { copy(conHorario = v) } })
            }
            if (formulario.conHorario) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonValor(
                        Icons.Outlined.Schedule,
                        stringResource(R.string.promo_hora_inicio, formulario.horaInicio?.let(::hora) ?: "—"),
                        Modifier.weight(1f)
                    ) { eligiendoHora = Extremo.INICIO }
                    BotonValor(
                        Icons.Outlined.Schedule,
                        stringResource(R.string.promo_hora_fin, formulario.horaFin?.let(::hora) ?: "—"),
                        Modifier.weight(1f)
                    ) { eligiendoHora = Extremo.FIN }
                }
            }
        }

        CampoPromo(
            formulario.cupos, { v -> onCambio { copy(cupos = v) } }, R.string.promo_cupos, errores.cupos,
            R.string.promo_cupos_ayuda, teclado = KeyboardType.Number
        )

        Button(onClick = onGuardar, enabled = !guardando, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            if (guardando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.promo_publicar))
        }
    }

    eligiendoFecha?.let { extremo ->
        SelectorFecha(
            inicial = if (extremo == Extremo.INICIO) formulario.desde else formulario.hasta ?: formulario.desde,
            minimo = if (extremo == Extremo.FIN) formulario.desde else null,
            onElegir = { dia ->
                onCambio {
                    if (extremo == Extremo.INICIO) copy(desde = dia, hasta = hasta?.takeIf { !it.isBefore(dia) } ?: dia)
                    else copy(hasta = dia)
                }
                eligiendoFecha = null
            },
            onCerrar = { eligiendoFecha = null }
        )
    }
    eligiendoHora?.let { extremo ->
        SelectorHora(
            inicial = (if (extremo == Extremo.INICIO) formulario.horaInicio else formulario.horaFin)
                ?: if (extremo == Extremo.INICIO) LocalTime.of(15, 0) else LocalTime.of(18, 0),
            onElegir = { h ->
                onCambio { if (extremo == Extremo.INICIO) copy(horaInicio = h) else copy(horaFin = h) }
                eligiendoHora = null
            },
            onCerrar = { eligiendoHora = null }
        )
    }
}

private enum class Extremo { INICIO, FIN }

@Composable
private fun Seccion(titulo: String, @StringRes errorRes: Int?, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        contenido()
        if (errorRes != null) {
            Text(stringResource(errorRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BotonValor(icono: ImageVector, texto: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icono, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CampoPromo(
    valor: String,
    onValor: (String) -> Unit,
    @StringRes etiquetaRes: Int,
    @StringRes errorRes: Int?,
    @StringRes ayudaRes: Int?,
    lineas: Int = 1,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(stringResource(etiquetaRes)) },
        isError = errorRes != null,
        singleLine = lineas == 1,
        minLines = lineas,
        supportingText = (errorRes ?: ayudaRes)?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = teclado),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFecha(inicial: LocalDate?, minimo: LocalDate?, onElegir: (LocalDate) -> Unit, onCerrar: () -> Unit) {
    val hoy = LocalDate.now()
    val desde = maxOf(hoy, minimo ?: hoy)
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = (inicial ?: desde).aMillisUtc(),
        selectableDates = object : SelectableDates {
            // No se pueden elegir días pasados ni un término anterior al inicio.
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= desde.aMillisUtc()
            override fun isSelectableYear(year: Int) = year >= desde.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { onElegir(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.promo_aceptar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.catalogo_cancelar)) } }
    ) { DatePicker(state = estado) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorHora(inicial: LocalTime, onElegir: (LocalTime) -> Unit, onCerrar: () -> Unit) {
    val estado = rememberTimePickerState(initialHour = inicial.hour, initialMinute = inicial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = { onElegir(LocalTime.of(estado.hour, estado.minute)) }) { Text(stringResource(R.string.promo_aceptar)) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.catalogo_cancelar)) } },
        text = { TimePicker(state = estado) }
    )
}

@Composable
private fun MensajePromociones(icono: ImageVector, @StringRes textoRes: Int, @StringRes botonRes: Int, onBoton: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(textoRes), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBoton) { Text(stringResource(botonRes)) }
    }
}

private val formatoFecha = DateTimeFormatter.ofPattern("d MMM yyyy", LocaleCL)
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

private fun fecha(dia: LocalDate): String = dia.format(formatoFecha)
private fun hora(h: LocalTime): String = h.format(formatoHora)
private fun nombreDia(dia: DayOfWeek): String =
    dia.getDisplayName(TextStyle.SHORT, LocaleCL).removeSuffix(".").replaceFirstChar { it.uppercase() }
private fun LocalDate.aMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
