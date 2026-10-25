package com.cocinacapital.app.ui.home

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion

/** Estado de la pantalla Home. La UI solo dibuja lo que este estado indica. */
sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Error(@androidx.annotation.StringRes val messageRes: Int) : HomeUiState

    data class Success(
        val categorias: List<Categoria>,
        /** `null` = chip "Todos". */
        val categoriaSeleccionadaId: String?,
        /** Sección "Para ti" filtrada por categoría y ordenada por afinidad. */
        val localesParaTi: List<LocalGastronomico>,
        /** Promociones filtradas por categoría, con distancia y orden por cercanía si hay GPS. */
        val promociones: List<Promocion>,
        val tieneUbicacion: Boolean = false
    ) : HomeUiState
}

/** Posición del usuario; la ausencia de ubicación se representa con null. */
data class UbicacionUsuario(
    val comuna: String,
    val latitud: Double,
    val longitud: Double
)
