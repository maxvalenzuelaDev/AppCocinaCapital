package com.cocinacapital.app.ui.experiencias

sealed interface ExperienciasUiState<out T> {
    data object Loading : ExperienciasUiState<Nothing>
    data object Error : ExperienciasUiState<Nothing>
    data class Success<T>(val datos: T) : ExperienciasUiState<T>
}
