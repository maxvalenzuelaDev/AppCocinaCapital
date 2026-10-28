package com.cocinacapital.app.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Matriz del QR de [contenido], de [lado] × [lado] módulos-pixel. Corrección de errores media: se
 * sigue leyendo con la pantalla algo rayada o con reflejos.
 */
fun generarQr(contenido: String, lado: Int): BitMatrix =
    QRCodeWriter().encode(
        contenido, BarcodeFormat.QR_CODE, lado, lado,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
    )

/** Texto del QR que aparece en la imagen ([pixeles] en ARGB, fila por fila), o `null` si no hay uno. */
fun leerQr(pixeles: IntArray, ancho: Int, alto: Int): String? {
    val imagen = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(ancho, alto, pixeles)))
    return try {
        QRCodeReader().decode(
            imagen,
            mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        ).text
    } catch (_: NotFoundException) {
        null
    } catch (_: ChecksumException) {
        null
    } catch (_: FormatException) {
        null
    }
}

/** Pixeles ARGB de la matriz (negro sobre blanco), para dibujarla o probar la lectura. */
fun BitMatrix.aPixeles(): IntArray = IntArray(width * height) { i ->
    if (get(i % width, i / width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
