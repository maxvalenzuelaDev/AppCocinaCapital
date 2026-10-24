package com.cocinacapital.app.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Archivo nuevo para la cámara: [uri] (content://) se entrega a la cámara y [ruta] se guarda en Room. */
data class ArchivoFoto(val uri: String, val ruta: String)

/**
 * Archivos de la foto de perfil. Se guardan en la carpeta privada de la app: los URI de la galería
 * pierden el permiso de lectura al cerrar la app, por eso la foto elegida se copia.
 * Interfaz para poder probar [com.cocinacapital.app.ui.perfil.PerfilViewModel] sin Android.
 */
interface FotoPerfilArchivos {
    fun nuevoArchivoCamara(): ArchivoFoto
    /** Copia la imagen de [uri] (selector de fotos) a un archivo propio y devuelve su ruta. */
    suspend fun copiarDesde(uri: String): String
    fun eliminar(ruta: String)
}

class AndroidFotoPerfilArchivos(private val context: Context) : FotoPerfilArchivos {

    private val carpeta: File get() = File(context.filesDir, CARPETA).apply { mkdirs() }

    /** Nombre único por foto: así Coil no muestra la imagen anterior desde su caché. */
    private fun nuevoArchivo() = File(carpeta, "perfil_${System.currentTimeMillis()}.jpg")

    override fun nuevoArchivoCamara(): ArchivoFoto {
        val archivo = nuevoArchivo()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        return ArchivoFoto(uri.toString(), archivo.absolutePath)
    }

    override suspend fun copiarDesde(uri: String): String = withContext(Dispatchers.IO) {
        val destino = nuevoArchivo()
        val entrada = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("No se pudo leer la imagen elegida")
        entrada.use { input -> destino.outputStream().use { input.copyTo(it) } }
        destino.absolutePath
    }

    override fun eliminar(ruta: String) {
        // Solo borra archivos de nuestra carpeta, nunca una ruta externa.
        val archivo = File(ruta)
        if (archivo.parentFile == carpeta) archivo.delete()
    }

    private companion object {
        const val CARPETA = "fotos_perfil"
    }
}
