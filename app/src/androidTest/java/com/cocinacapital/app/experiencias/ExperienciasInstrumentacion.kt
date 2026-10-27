package com.cocinacapital.app.experiencias

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
import com.cocinacapital.app.data.model.Experiencia
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.ui.experiencias.*
import com.cocinacapital.app.ui.theme.CocinaCapitalTheme
import java.io.File

/** Pruebas visuales con fixtures locales. Vive solo en el APK de pruebas; no escribe en Supabase. */
class ExperienciasInstrumentacion : Instrumentation() {
    private var oscuro = false
    private var vista by mutableStateOf(0)
    private var fuente by mutableFloatStateOf(1f)
    private var localAbierto: String? = null
    private var reintentos = 0
    private val local = LocalGastronomico("local-controlado", "Cocina del Puerto", "c", "Valparaíso", direccion = "Av. Errázuriz 1120")
    private val experiencia = Experiencia("experiencia-controlada", local.id, "Menú de temporada",
        descripcion = "Una propuesta de cuatro tiempos con ingredientes de estación. Incluye entrada, dos preparaciones principales y postre.",
        horario = "Viernes y sábado, de 19:00 a 22:00", precioReferencial = 150000, local = local)
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        oscuro = arguments?.getString("modo") == "oscuro"
        start()
    }
    override fun onStart() {
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val actual = activity
            runOnMainSync {
                actual.setContent {
                    CocinaCapitalTheme(darkTheme = oscuro) {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, fuente)) {
                            when (vista) {
                                0 -> ExperienciasContent(ExperienciasUiState.Success(listOf(experiencia)),
                                    onVolver = {}, onDetalle = { check(it == experiencia.id); vista = 1 }, onReintentar = { reintentos++ })
                                1 -> DetalleExperienciaContent(ExperienciasUiState.Success(experiencia),
                                    onVolver = { vista = 0 }, onLocal = { localAbierto = it }, onReintentar = { reintentos++ })
                                2 -> ExperienciasContent(ExperienciasUiState.Success(emptyList()), {}, {}, { reintentos++ })
                                3 -> ExperienciasContent(ExperienciasUiState.Error, {}, {}, { reintentos++ })
                                else -> DetalleExperienciaContent(ExperienciasUiState.Success(null), {}, {}, { reintentos++ })
                            }
                        }
                    }
                }
            }
            esperar("Menú de temporada"); captura("listado")
            pulsar("Menú de temporada")
            esperar("Horario de la experiencia"); captura("detalle")
            pulsar("Ver local")
            check(localAbierto == local.id) { "El acceso al local usa un id incorrecto" }
            pulsar("Volver"); esperar("Experiencias")
            runOnMainSync { vista = 2 }; esperar("Aún no hay experiencias publicadas.", parcial = true)
            captura("vacio"); pulsar("Reintentar")
            runOnMainSync { vista = 3 }; esperar("No pudimos cargar las experiencias.", parcial = true)
            captura("error"); pulsar("Reintentar"); check(reintentos == 2)
            runOnMainSync { vista = 4 }; esperar("Esta experiencia ya no está disponible.")
            captura("no-disponible")
            runOnMainSync { vista = 1; fuente = 2f }
            esperar("Ver local"); captura("detalle-fuente-200")
            pulsar("Ver local"); check(localAbierto == local.id)
            finish(Activity.RESULT_OK, Bundle().apply { putString("resultado", "Listado, detalle, local, volver, vacío, error, ausencia y texto 200% correctos") })
        } catch (error: Throwable) {
            runCatching { captura("fallo") }
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("error", error.javaClass.simpleName + ": " + error.message) })
        } finally {
            activity?.let { runOnMainSync { it.finish() } }
        }
    }
    private fun nodos(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun recorrer(n: AccessibilityNodeInfo) {
            result.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let(::recorrer)
        }
        uiAutomation.rootInActiveWindow?.let(::recorrer)
        return result
    }
    private fun esperar(texto: String, parcial: Boolean = false) {
        repeat(35) {
            waitForIdleSync()
            if (nodos().any { if (parcial) it.text?.toString()?.contains(texto) == true
                else it.text?.toString() == texto || it.contentDescription?.toString() == texto }) return
            Thread.sleep(200)
        }
        error("No aparece: $texto")
    }
    private fun pulsar(texto: String) {
        esperar(texto)
        var node: AccessibilityNodeInfo? = nodos().first { it.text?.toString() == texto || it.contentDescription?.toString() == texto }
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "No se pudo pulsar $texto" }
        Thread.sleep(400)
    }
    private fun captura(nombre: String) {
        waitForIdleSync(); Thread.sleep(400)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        val folder = checkNotNull(targetContext.getExternalFilesDir("hu09-pruebas"))
        File(folder, nombre + (if (oscuro) "-oscuro" else "-claro") + ".png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
