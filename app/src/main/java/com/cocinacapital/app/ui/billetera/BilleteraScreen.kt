package com.cocinacapital.app.ui.billetera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cocinacapital.app.data.model.PromocionGuardada
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de la billetera de Cocina Capital.
 *
 * Muestra las promociones guardadas del consumidor
 * utilizando los datos reales obtenidos desde Supabase.
 */
@Composable
fun BilleteraScreen(
    onExplorarPromociones: () -> Unit = {},
    viewModel: BilleteraViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Actualiza la billetera al volver a esta pantalla.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.cargarBeneficios()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Encabezado.
            Text(
                text = "Mi billetera",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tus promociones gastronómicas guardadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                // Estado de carga.
                state.cargando -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Error al consultar Supabase.
                state.mensajeError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.mensajeError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = viewModel::cargarBeneficios
                        ) {
                            Text("Reintentar")
                        }
                    }
                }

                // El usuario no tiene promociones guardadas.
                state.beneficios.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Tu billetera está vacía",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Todavía no tienes promociones guardadas. Explora los locales y guarda tus beneficios favoritos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onExplorarPromociones,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkAdd,
                                contentDescription = null
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text("Explorar promociones")
                        }
                    }
                }

                // El usuario tiene promociones guardadas.
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Promociones guardadas: ${state.beneficios.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Tarjetas con información real.
                        state.promocionesGuardadas.forEach { item ->
                            TarjetaPromocionGuardada(
                                item = item,
                                procesando = state.procesando,
                                onEliminar = {
                                    viewModel.eliminarBeneficio(
                                        item.promocion.id
                                    )
                                }
                            )
                        }

                        if (state.promocionesGuardadas.isEmpty()) {
                            Text(
                                text = "No se pudo mostrar el detalle de las promociones guardadas.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta individual de una promoción guardada.
 */
@Composable
private fun TarjetaPromocionGuardada(
    item: PromocionGuardada,
    procesando: Boolean,
    onEliminar: () -> Unit
) {
    val promocion = item.promocion

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Título real de la promoción.
            Text(
                text = promocion.titulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Nombre del local.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = item.nombreLocal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Descuento, cuando está informado.
            if (promocion.descuentoPorcentaje > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "${promocion.descuentoPorcentaje}% de descuento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Fecha de vencimiento.
            formatearFechaChile(promocion.vigenteHasta)?.let { fecha ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Válida hasta el $fecha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Permite eliminar la promoción de la billetera.
            OutlinedButton(
                onClick = onEliminar,
                enabled = !procesando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Quitar de mi billetera")
            }
        }
    }
}

/**
 * Convierte la fecha de Supabase a un formato
 * legible utilizando la zona horaria de Chile.
 *
 * Ejemplo:
 * 2026-11-04T02:59:59+00:00
 * -> 3 de noviembre de 2026, 23:59
 */
private fun formatearFechaChile(fecha: String): String? {
    return try {
        val fechaChile = OffsetDateTime
            .parse(fecha)
            .atZoneSameInstant(
                ZoneId.of("America/Santiago")
            )

        val formato = DateTimeFormatter.ofPattern(
            "d 'de' MMMM 'de' yyyy, HH:mm",
            Locale.forLanguageTag("es-CL")
        )

        fechaChile.format(formato)

    } catch (e: Exception) {
        null
    }
}

