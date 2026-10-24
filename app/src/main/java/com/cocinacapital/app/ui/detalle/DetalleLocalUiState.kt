package com.cocinacapital.app.ui.detalle

import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Plato
import com.cocinacapital.app.data.model.Promocion

/** Estado de la pantalla de detalle de un local. Los textos de error viven en strings.xml. */
sealed interface DetalleLocalUiState {

    data object Loading : DetalleLocalUiState

    data object Error : DetalleLocalUiState

    /** El local no existe o ya no está activo. */
    data object NoEncontrado : DetalleLocalUiState

    data class Success(
        val local: LocalGastronomico,
        val platosDestacados: List<Plato>,
        val promociones: List<Promocion>
    ) : DetalleLocalUiState
}
