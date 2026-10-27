package com.cocinacapital.app.ui.perfil

import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.PerfilUsuario
import com.cocinacapital.app.data.model.PreferenciasUsuario

sealed interface PerfilUiState {
    data object Loading : PerfilUiState
    data class Error(@androidx.annotation.StringRes val mensajeRes: Int) : PerfilUiState
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

/**
 * Flujo para cambiar la foto de perfil (T-23). El ViewModel decide qué diálogo mostrar y qué abrir;
 * la pantalla solo dibuja el [dialogo] y ejecuta la [accion] una vez (después avisa con `onAccionFotoRealizada`).
 */
data class FotoPerfilUiState(
    val dialogo: DialogoFoto? = null,
    val accion: AccionFoto? = null
)

enum class DialogoFoto {
    /** Tomar foto, elegir de la galería o quitar la actual. */
    ELEGIR_ORIGEN,
    /** Explicación antes de pedir el permiso de cámara. */
    EXPLICAR_PERMISO,
    /** El usuario negó el permiso: mensaje y opción de abrir los ajustes. */
    PERMISO_DENEGADO,
    ERROR_GUARDAR
}

sealed interface AccionFoto {
    data object PedirPermisoCamara : AccionFoto
    data class AbrirCamara(val uri: String) : AccionFoto
    data object AbrirGaleria : AccionFoto
    data object AbrirAjustes : AccionFoto
}
