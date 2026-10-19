package com.cocinacapital.app.ui.home

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion

/** Estado de la pantalla Home. La UI solo dibuja lo que este estado indica. */
sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Error(val message: String) : HomeUiState

    data class Success(
        val categorias: List<Categoria>,
        /** `null` = chip "Todos". */
        val categoriaSeleccionadaId: String?,
        /** Sección "Para ti" (ya filtrada por búsqueda/categoría y ordenada por afinidad). */
        val localesParaTi: List<LocalGastronomico>,
        /** Sección "Promociones vigentes" (ya filtrada). */
        val promociones: List<Promocion>
    ) : HomeUiState
}

/** Ubicación actual del usuario. Por ahora es un valor por defecto (ver HomeViewModel). */
data class UbicacionUsuario(
    val comuna: String,
    val latitud: Double,
    val longitud: Double
)
