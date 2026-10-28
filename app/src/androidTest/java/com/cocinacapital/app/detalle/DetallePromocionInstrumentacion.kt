package com.cocinacapital.app.detalle

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.cocinacapital.app.MainActivity
import com.cocinacapital.app.data.model.*
import com.cocinacapital.app.ui.detalle.*
import com.cocinacapital.app.ui.theme.CocinaCapitalTheme
import java.io.File
import java.time.Instant

/** Casos controlados del detalle, aislados del catálogo compartido. */
class DetallePromocionInstrumentacion : Instrumentation() {
    private val ahora = Instant.parse("2026-10-27T18:00:00Z")
    private val promo = Promocion("p", "l", "Combo del día", descripcion = "Burger, papas y bebida entre 15:00 y 18:00.",
        descuentoPorcentaje = 25, vigenteDesde = "2026-10-27T03:00:00Z", vigenteHasta = "2026-10-28T03:00:00Z",
        condiciones = "Consumo en local.", cuposTotal = 10, cuposDisponibles = 10)
    private val local = LocalGastronomico("l", "Burger Pacífico", "c", "Viña del Mar", direccion = "Av. San Martín 678")
    private var estado by mutableStateOf<DetallePromocionUiState>(success(promo))
    private var fuente by mutableFloatStateOf(1f)
    private var oscuro = false
    private var abierto: String? = null
    private var reintentos = 0
    private fun success(p: Promocion) = DetallePromocionUiState.Success(p, local,
        p.vigenciaParaDetalle(ahora, true), cuposConfigurados = true, horarioConfigurado = true)
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); oscuro = arguments?.getString("modo") == "oscuro"; start() }
    override fun onStart() {
        var activity: MainActivity? = null
        var resultado = Activity.RESULT_OK
        val datos = Bundle()
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val actual = activity
            runOnMainSync { actual.setContent {
                CocinaCapitalTheme(darkTheme = oscuro) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fuente)) {
                        DetallePromocionContent(estado, {}, { abierto = it }, { reintentos++ })
                    }
                }
            } }
            esperar("10 cupos disponibles"); captura("disponible")
            pulsar("Ver local"); check(abierto == "l")
            buscarDesplazando("De 15:00 a 18:00")
            esperar("Horario indicado en la descripción de la promoción.")
            captura("horario-antiguo"); volverArriba()
            runOnMainSync { estado = success(promo.copy(cuposDisponibles = 0)) }
            esperar("Sin cupos disponibles"); captura("agotada")
            runOnMainSync { estado = success(promo.copy(activa = false)) }
            esperar("No disponible"); captura("pausada")
            runOnMainSync { estado = success(promo).copy(vigencia = promo.vigenciaParaDetalle(Instant.parse("2026-10-27T17:00:00Z"), true)) }
            esperar("Fuera del horario de uso"); captura("fuera-horario")
            runOnMainSync { estado = DetallePromocionUiState.Error }
            esperar("Reintentar"); pulsar("Reintentar"); check(reintentos == 1); captura("error")
            runOnMainSync { estado = DetallePromocionUiState.NoEncontrada }
            esperar("Esta promoción no está disponible."); captura("no-encontrada")
            runOnMainSync { estado = success(promo).copy(local = null, errorLocal = true) }
            buscarDesplazando("No pudimos cargar la información del local.")
            buscarDesplazando("Reintentar")
            pulsar("Reintentar"); check(reintentos == 2); captura("error-local")
            runOnMainSync { estado = success(promo); fuente = 2f }
            volverArriba(); esperar("Ver local"); captura("fuente-200-superior")
            buscarDesplazando("Av. San Martín 678"); captura("fuente-200-local")
            pulsar("Ver local"); check(abierto == "l")
            datos.putString("resultado", "Detalle, horario antiguo, agotada, pausada, fuera de horario, error, ausencia, fallo del local y texto 200% correctos")
        } catch (error: Throwable) {
            resultado = Activity.RESULT_CANCELED
            datos.putString("error", error.javaClass.simpleName + ": " + error.message)
            runCatching { captura("fallo") }
        } finally { activity?.let { runOnMainSync { it.finish() } } }
        finish(resultado, datos)
    }
    private fun nodos(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun recorrer(n: AccessibilityNodeInfo) { result.add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::recorrer) }
        uiAutomation.rootInActiveWindow?.let(::recorrer)
        return result
    }
    private fun coincide(n: AccessibilityNodeInfo, texto: String) = n.isVisibleToUser && (n.text?.toString() == texto || n.contentDescription?.toString() == texto)
    private fun esperar(texto: String) {
        repeat(35) { waitForIdleSync(); if (nodos().any { coincide(it, texto) }) return; Thread.sleep(200) }
        error("No aparece: $texto")
    }
    private fun volverArriba() {
        repeat(12) {
            waitForIdleSync()
            if (nodos().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) != true) return
            Thread.sleep(250)
        }
    }
    private fun buscarDesplazando(texto: String) {
        Thread.sleep(300)
        repeat(20) {
            waitForIdleSync()
            if (nodos().any { coincide(it, texto) }) return
            nodos().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(350)
        }
        error("No se alcanzó: $texto")
    }
    private fun pulsar(texto: String) {
        esperar(texto)
        var node: AccessibilityNodeInfo? = nodos().first { coincide(it, texto) }
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        Thread.sleep(350)
    }
    private fun captura(nombre: String) {
        waitForIdleSync(); Thread.sleep(350)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        val folder = checkNotNull(targetContext.getExternalFilesDir("hu12-cierre"))
        File(folder, nombre + (if (oscuro) "-oscuro" else "-claro") + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
