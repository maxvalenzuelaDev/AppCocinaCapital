package com.cocinacapital.app.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.TipoFavorito
import com.cocinacapital.app.data.repository.DetalleLocalRepository
import com.cocinacapital.app.data.repository.FavoritosRepository
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.data.repository.Interaccion
import com.cocinacapital.app.data.repository.RegistroInteracciones
import com.cocinacapital.app.data.repository.TipoInteraccion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel del detalle de un local (RF-05): carga local, menú destacado y promociones en paralelo. */
class DetalleLocalViewModel(
    private val localId: String,
    private val repository: DetalleLocalRepository,
    private val favoritos: FavoritosRepository,
    private val interacciones: RegistroInteracciones = RegistroInteracciones.Ninguno
) : ViewModel() {

    /** HU-20: la vista se registra una vez por apertura, no en cada reintento. */
    private var vistaRegistrada = false

    private val _uiState = MutableStateFlow<DetalleLocalUiState>(DetalleLocalUiState.Loading)
    val uiState: StateFlow<DetalleLocalUiState> = _uiState.asStateFlow()

    /** Si el local está en favoritos (Room); pinta el corazón de la barra superior. */
    val esFavorito: StateFlow<Boolean> = favoritos.observarIds(TipoFavorito.LOCAL)
        .map { localId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    /** Agrega o quita el local de favoritos. Solo cuando ya cargó (se necesita el nombre para la lista del Perfil). */
    fun onFavoritoClick() {
        val local = (_uiState.value as? DetalleLocalUiState.Success)?.local ?: return
        viewModelScope.launch {
            favoritos.alternar(
                Favorito(
                    itemId = local.id,
                    tipo = TipoFavorito.LOCAL,
                    nombre = local.nombre,
                    subtitulo = "${local.categoriaNombre} · ${local.comuna}",
                    imagenUrl = local.imagenUrl
                )
            )
        }
    }

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = DetalleLocalUiState.Loading
            _uiState.value = try {
                coroutineScope {
                    val local = async { repository.getLocal(localId) }
                    val platos = async { repository.getPlatosDestacados(localId) }
                    val promociones = async { repository.getPromocionesVigentes(localId) }
                    val localOk = local.await()
                    if (localOk == null) {
                        DetalleLocalUiState.NoEncontrado
                    } else {
                        if (!vistaRegistrada) {
                            vistaRegistrada = true
                            interacciones.registrar(Interaccion(TipoInteraccion.VISTA_LOCAL, localId = localOk.id))
                        }
                        DetalleLocalUiState.Success(
                            local = localOk,
                            platosDestacados = platos.await(),
                            // La tarjeta de promoción muestra el nombre del local.
                            promociones = promociones.await().map {
                                it.copy(localNombre = localOk.nombre, localCategoriaId = localOk.categoriaId)
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e // nunca tragarse la cancelación de corrutinas
            } catch (e: Exception) {
                DetalleLocalUiState.Error
            }
        }
    }

    companion object {
        fun factory(localId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetalleLocalViewModel(
                    localId, AppContainer.detalleLocalRepository, AppContainer.favoritosRepository,
                    AppContainer.registroInteracciones
                )
            }
        }
    }
}
