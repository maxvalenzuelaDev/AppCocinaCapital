package com.cocinacapital.app.ui.detalle

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.model.VigenciaPromocion

sealed interface DetallePromocionUiState {
    data object Loading : DetallePromocionUiState
    data object Error : DetallePromocionUiState
    data object NoEncontrada : DetallePromocionUiState

    data class Success(
        val promocion: Promocion,
        val local: LocalGastronomico?,
        val vigencia: VigenciaPromocion,
        val errorLocal: Boolean = false,
        val cuposConfigurados: Boolean = false,
        val horarioConfigurado: Boolean = false
    ) : DetallePromocionUiState
}
