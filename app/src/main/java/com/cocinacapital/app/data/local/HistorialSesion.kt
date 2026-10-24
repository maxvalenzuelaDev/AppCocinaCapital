package com.cocinacapital.app.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * Recuerda en el teléfono si alguna vez se inició sesión, para que el Login salude distinto
 * en el primer uso ("Te damos la bienvenida") y a quien vuelve ("Qué bueno volver a verte").
 * No guarda datos del usuario, solo un sí/no. Interfaz para probar AuthViewModel sin Android.
 */
interface HistorialSesion {
    fun huboSesion(): Boolean
    fun registrarSesion()
}

class PreferenciasHistorialSesion(context: Context) : HistorialSesion {

    private val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    override fun huboSesion(): Boolean = prefs.getBoolean(CLAVE, false)

    override fun registrarSesion() = prefs.edit { putBoolean(CLAVE, true) }

    private companion object {
        const val ARCHIVO = "historial_sesion"
        const val CLAVE = "hubo_sesion"
    }
}
