package com.cocinacapital.app.data.remote

import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** TLS moderno solo en el cliente de rutas; mantiene los certificados del sistema y la validación del host. */
internal fun crearClienteRutas(): HttpClient {
    val legacy = if (Build.VERSION.SDK_INT < 29) {
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }.trustManagers.filterIsInstance<X509TrustManager>().single()
        val tls = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply { init(null, arrayOf(trust), null) }
        OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust).build()
    } else null
    return HttpClient(OkHttp) {
        if (legacy != null) engine { preconfigured = legacy }
        // OSRM también devuelve NoSegment/NoRoute con HTTP 400; el repositorio interpreta su código.
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
    }
}
