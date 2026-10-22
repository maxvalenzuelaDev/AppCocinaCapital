package com.cocinacapital.app.ui.perfil

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.PerfilUsuario
import com.cocinacapital.app.data.model.PreferenciasUsuario

sealed interface PerfilUiState {
    data object Loading : PerfilUiState
    data class Error(val message: String) : PerfilUiState
    data class Success(
        /** `null` = no hay sesión (la app debería volver al Login). */
        val usuario: PerfilUsuario?,
        val preferencias: PreferenciasUsuario,
        val categorias: List<Categoria>,
        val favoritos: List<Favorito>
    ) : PerfilUiState {
        val canjeados: Int get() = 0 // TODO(BD): contar beneficios canjeados (tabla `beneficios`)
    }
}
