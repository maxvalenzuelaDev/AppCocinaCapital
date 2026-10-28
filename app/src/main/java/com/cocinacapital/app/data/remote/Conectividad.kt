package com.cocinacapital.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Si el teléfono tiene internet (HU-35): muestra el aviso "Sin conexión" y reintenta al volver. */
interface Conectividad {
    val conectado: StateFlow<Boolean>
}

/** Cada vez que vuelve la conexión después de haberse perdido. */
fun Conectividad.reconexiones(): Flow<Unit> =
    conectado.drop(1).filter { it }.map { }

class AndroidConectividad(context: Context) : Conectividad {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val estado = MutableStateFlow(hayInternet())
    override val conectado: StateFlow<Boolean> = estado.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                estado.value = capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                estado.value = false
            }
        })
    }

    private fun hayInternet(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
