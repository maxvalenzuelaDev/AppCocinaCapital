package com.cocinacapital.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Convierte la foto elegida en un JPEG liviano para subirlo (T-05). */
interface PreparadorFoto {
    suspend fun jpeg(uri: String): ByteArray
}

/**
 * Reduce la foto a [ladoMaximo] píxeles por su lado más largo, la endereza según su orientación
 * y la comprime en JPEG: una foto de cámara de varios MB queda en unos pocos cientos de KB.
 */
class AndroidPreparadorFoto(
    private val context: Context,
    private val ladoMaximo: Int = 1280,
    private val calidad: Int = 82
) : PreparadorFoto {

    override suspend fun jpeg(uri: String): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = decodificar(Uri.parse(uri))
        ByteArrayOutputStream().use { salida ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
            bitmap.recycle()
            salida.toByteArray()
        }
    }

    private fun decodificar(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder ya aplica la orientación de la foto.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val escala = escala(info.size.width, info.size.height)
                if (escala < 1f) decoder.setTargetSize((info.size.width * escala).roundToInt(), (info.size.height * escala).roundToInt())
            }
        } else {
            decodificarAntiguo(uri)
        }

    /** Android 8: BitmapFactory no aplica la orientación, se lee del EXIF y se gira a mano. */
    private fun decodificarAntiguo(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
            ?: throw IOException("No se pudo leer la imagen elegida")
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        val leida = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: throw IOException("No se pudo leer la imagen elegida")
        val grados = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val escala = escala(leida.width, leida.height)
        if (grados == 0f && escala >= 1f) return leida
        val matriz = Matrix().apply {
            if (escala < 1f) postScale(escala, escala)
            postRotate(grados)
        }
        return Bitmap.createBitmap(leida, 0, 0, leida.width, leida.height, matriz, true).also {
            if (it != leida) leida.recycle()
        }
    }

    private fun escala(ancho: Int, alto: Int): Float = minOf(1f, ladoMaximo.toFloat() / max(ancho, alto))
}
