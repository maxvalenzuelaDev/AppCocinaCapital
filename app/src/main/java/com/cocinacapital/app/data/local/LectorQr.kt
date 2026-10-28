package com.cocinacapital.app.data.local

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.cocinacapital.app.util.leerQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lectura del QR de un beneficio desde una foto (HU-29). Usa la app de cámara del teléfono, como la
 * foto de perfil (T-23), y lee el código con zxing: no necesita CameraX ni servicios de Google.
 */
interface LectorQr {
    /** Archivo temporal donde la cámara guarda la foto. */
    fun nuevaCaptura(): ArchivoFoto
    /** Texto del QR que aparece en la foto, o `null`. Borra la foto al terminar. */
    suspend fun leer(ruta: String): String?
    /** Borra la foto cuando se cancela la cámara. */
    fun descartar(ruta: String)
}

class AndroidLectorQr(private val context: Context) : LectorQr {

    private val carpeta: File get() = File(context.cacheDir, "escaneos_qr").apply { mkdirs() }

    override fun nuevaCaptura(): ArchivoFoto {
        val archivo = File(carpeta, "qr_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        return ArchivoFoto(uri.toString(), archivo.absolutePath)
    }

    override suspend fun leer(ruta: String): String? = withContext(Dispatchers.Default) {
        try {
            // Se prueba a dos tamaños: a veces el código se encuentra mejor con menos detalle.
            LADOS.firstNotNullOfOrNull { lado -> leerReducida(ruta, lado) }
        } finally {
            File(ruta).delete()
        }
    }

    override fun descartar(ruta: String) {
        File(ruta).delete()
    }

    /** Carga la foto reducida (una de 12 MP ocuparía ~48 MB como pixeles) y busca el QR. */
    private fun leerReducida(ruta: String, ladoMaximo: Int): String? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
        val foto = BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return null
        return try {
            val pixeles = IntArray(foto.width * foto.height)
            foto.getPixels(pixeles, 0, foto.width, 0, 0, foto.width, foto.height)
            leerQr(pixeles, foto.width, foto.height)
        } finally {
            foto.recycle()
        }
    }

    private companion object {
        val LADOS = listOf(1600, 800)
    }
}
