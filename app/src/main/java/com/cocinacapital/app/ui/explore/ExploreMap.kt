package com.cocinacapital.app.ui.explore

import android.annotation.SuppressLint
import android.net.Uri
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cocinacapital.app.R
import com.cocinacapital.app.BuildConfig
import com.cocinacapital.app.ui.theme.LocalDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Ruta
import com.cocinacapital.app.ui.home.UbicacionUsuario
import org.json.JSONArray
import org.json.JSONObject

/** Leaflet y el trazado se dibujan localmente; las teselas se descargan por HTTPS. */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable fun ExploreMap(locals: List<LocalGastronomico>, position: UbicacionUsuario?, selectedId: String?,
    onSelected: (String) -> Unit, positionLabel: String, onCleared: () -> Unit = {},
    ruta: Ruta? = null, compacto: Boolean = false, bottomInsetDp: Int = 24, topInsetDp: Int = 24, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onSelected)
    val clearCallback by rememberUpdatedState(onCleared)
    val dark = LocalDarkTheme.current
    val appContext = LocalContext.current
    val labels = JSONObject().apply {
        put("loading", appContext.getString(R.string.ex_map_cargando))
        put("error", appContext.getString(R.string.ex_map_error))
        put("slow", appContext.getString(R.string.ex_map_tardando))
        put("empty", appContext.getString(R.string.ex_map_vacio))
        put("origin", appContext.getString(R.string.ex_map_sin_origen))
        put("position", appContext.getString(R.string.ex_map_posicion))
        put("locals", appContext.getString(R.string.ex_map_ver_locales))
        put("route", appContext.getString(R.string.ruta_centrar))
        put("zoomIn", appContext.getString(R.string.ex_map_acercar))
        put("zoomOut", appContext.getString(R.string.ex_map_alejar))
    }
    val payload = JSONObject().apply {
        put("dark", dark)
        put("positionLabel", positionLabel)
        put("labels", labels)
        put("bottomInset", bottomInsetDp)
        put("topInset", topInsetDp)
        put("compact", compacto)
        put("routeId", ruta?.let { "$selectedId:${it.hashCode()}" } ?: "")
        put("route", JSONArray().apply { ruta?.puntos?.forEach { p -> put(JSONArray().put(p.latitud).put(p.longitud)) } })
        put("locals", JSONArray().apply { locals.forEach { local ->
            if (local.latitud != null && local.longitud != null) put(JSONObject().apply {
                put("id", local.id); put("name", local.nombre); put("lat", local.latitud); put("lon", local.longitud)
            })
        } })
        put("position", position?.let { JSONObject().put("lat", it.latitud).put("lon", it.longitud) } ?: JSONObject.NULL)
        put("selected", selectedId ?: JSONObject.NULL)
    }.toString()
    val currentPayload by rememberUpdatedState(payload)
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            setBackgroundColor(if (dark) android.graphics.Color.rgb(24, 26, 28) else android.graphics.Color.rgb(242, 242, 242))
            settings.javaScriptEnabled = true
            // El mapa controla su paleta; evitar que WebView vuelva a oscurecerlo.
            if (android.os.Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
            else if (android.os.Build.VERSION.SDK_INT >= 29) {
                @Suppress("DEPRECATION")
                settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
            }
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.userAgentString += " AppCocinaCapital/1.0"
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    // Recursos empaquetados: no se ejecutan scripts de un CDN ni se permite leer otros archivos.
                    val assets = setOf("/assets/maplibre-5.24.0.js", "/assets/maplibre-5.24.0.css", "/assets/maplibre-leaflet-0.1.4.js")
                    if (request.method == "GET" && uri.scheme == "https" && uri.host == "cocinacapital.local" && uri.path in assets) {
                        val name = uri.lastPathSegment ?: return null
                        return WebResourceResponse(if (name.endsWith(".css")) "text/css" else "application/javascript", "UTF-8", context.assets.open(name))
                    }
                    return null
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri: Uri = request.url
                    if (uri.scheme == "cc-select" && uri.host == "local") {
                        uri.lastPathSegment?.let { id -> view.post { callback(id) } }
                    }
                    if (uri.scheme == "cc-clear") view.post { clearCallback() }
                    if (uri.scheme == "https" && uri.host in setOf("www.openstreetmap.org", "routing.openstreetmap.de", "www.openmaptiles.org")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                    return true
                }
                override fun onPageFinished(view: WebView, url: String) {
                    val density = view.resources.displayMetrics.density
                    view.evaluateJavascript("window.setExploreViewport(${view.width / density},${view.height / density});window.updateExplore($currentPayload)", null)
                }
            }
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                val density = view.resources.displayMetrics.density
                evaluateJavascript("if(window.setExploreViewport)window.setExploreViewport(${view.width / density},${view.height / density})", null)
            }
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(event.action != android.view.MotionEvent.ACTION_UP && event.action != android.view.MotionEvent.ACTION_CANCEL)
                false
            }
            val css = context.assets.open("leaflet.css").bufferedReader().use { it.readText() }
            val js = context.assets.open("leaflet.js").bufferedReader().use { it.readText() }
            val html = context.assets.open("explore-map.html").bufferedReader().use { it.readText() }
                .replace("INITIAL_MAP_THEME", if (dark) "dark" else "light")
                .replace("/*INITIAL_LABELS*/", labels.toString())
                .replace("INITIAL_LOADING_TEXT", android.text.TextUtils.htmlEncode(context.getString(R.string.ex_map_cargando)))
                .replace("INITIAL_ARIA_LABEL", android.text.TextUtils.htmlEncode(context.getString(R.string.ex_map_aria)))
                .replace("/*LEAFLET_CSS*/", css).replace("/*LEAFLET_JS*/", js)
            loadDataWithBaseURL("https://cocinacapital.local/", html, "text/html", "UTF-8", null)
        }
    }, update = { it.evaluateJavascript("if(window.updateExplore)window.updateExplore($payload)", null) },
        onRelease = { it.stopLoading(); it.destroy() })
}
