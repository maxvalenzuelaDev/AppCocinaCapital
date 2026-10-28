package com.cocinacapital.app.ui.location

import com.cocinacapital.app.data.location.EstadoUbicacion
import com.cocinacapital.location.LocationProblem
import com.cocinacapital.location.UserPosition

sealed interface UbicacionUiState {
    data object Initial : UbicacionUiState
    data class Loading(val posicionAnterior: UserPosition?) : UbicacionUiState
    data class Success(val posicion: UserPosition) : UbicacionUiState
    data class Error(val problema: LocationProblem) : UbicacionUiState
}

internal fun EstadoUbicacion.paraInterfaz(): UbicacionUiState = when {
    cargando -> UbicacionUiState.Loading(posicion)
    problema != null -> UbicacionUiState.Error(problema)
    posicion != null -> UbicacionUiState.Success(posicion)
    else -> UbicacionUiState.Initial
}
