package com.cocinacapital.app.data.model

/**
 * Código de canje de un beneficio: 8 caracteres que genera Supabase sin 0/O ni 1/I/L.
 * Se muestra como "ABCD-EFGH" y el QR lleva el prefijo [PREFIJO_QR] para reconocer que es nuestro.
 */
object CodigoBeneficio {
    const val LARGO = 8
    const val PREFIJO_QR = "cocinacapital:beneficio:"

    /** Mayúsculas y sin guiones ni espacios, como lo compara Supabase. */
    fun normalizar(texto: String): String = texto.filter { it.isLetterOrDigit() }.uppercase()

    /** "ABCDEFGH" → "ABCD-EFGH" para leerlo en voz alta o escribirlo a mano. */
    fun formatear(codigo: String): String {
        val limpio = normalizar(codigo)
        return if (limpio.length == LARGO) "${limpio.take(4)}-${limpio.drop(4)}" else limpio
    }

    fun contenidoQr(codigo: String): String = PREFIJO_QR + normalizar(codigo)

    /**
     * Código que trae un QR leído por el oferente o escrito a mano; `null` si no tiene la forma de
     * un código nuestro (por ejemplo, el QR de un sitio web).
     */
    fun desdeTexto(texto: String): String? {
        val recortado = texto.trim()
        val sinPrefijo = if (recortado.startsWith(PREFIJO_QR, ignoreCase = true)) recortado.drop(PREFIJO_QR.length)
        else recortado
        if (sinPrefijo.any { !it.isLetterOrDigit() && it != '-' && it != ' ' }) return null
        return normalizar(sinPrefijo).takeIf { it.length == LARGO }
    }
}
