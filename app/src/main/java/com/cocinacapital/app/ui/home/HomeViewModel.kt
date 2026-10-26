package com.cocinacapital.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.util.distanciaKm
import com.cocinacapital.app.util.normalizado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del Home.
 *
 * Idea clave: se descargan los datos UNA vez (estado "crudo" [DataState]) y luego
 * [uiState] se recalcula con `combine` cada vez que cambia la búsqueda, la categoría o la
 * ubicación. Así filtrar es instantáneo y no hace llamadas de red por cada letra.
 */
class HomeViewModel(
    private val repository: HomeRepository,
    private val ubicaciones: com.cocinacapital.app.data.location.UbicacionRepository
) : ViewModel() {

    // ---- Estado crudo de la carga remota (privado) ----
    private sealed interface DataState {
        data object Loading : DataState
        data class Loaded(
            val categorias: List<Categoria>,
            val locales: List<LocalGastronomico>,
            val promociones: List<Promocion>
        ) : DataState
        data class Failed(val message: String) : DataState
    }

    private val dataState = MutableStateFlow<DataState>(DataState.Loading)
    private val _searchQuery = MutableStateFlow("")
    private val categoriaSeleccionadaId = MutableStateFlow<String?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val ubicacion = ubicaciones.posicion

    /** Estado único que observa la pantalla. */
    val uiState: StateFlow<HomeUiState> = combine(
        dataState, _searchQuery, categoriaSeleccionadaId, ubicacion
    ) { data, query, categoriaId, ubicacion ->
        when (data) {
            DataState.Loading -> HomeUiState.Loading
            is DataState.Failed -> HomeUiState.Error(data.message)
            is DataState.Loaded -> construirSuccess(data, query, categoriaId, ubicacion)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000), // sobrevive a rotaciones
        initialValue = HomeUiState.Loading
    )

    init {
        cargarHome()
    }

    // ---------------- Acciones de la UI ----------------

    fun onSearchQueryChange(texto: String) = _searchQuery.update { texto }

    /** Tocar el chip seleccionado lo deselecciona (vuelve a "Todos"). */
    fun onCategoriaClick(id: String?) =
        categoriaSeleccionadaId.update { actual -> if (id == actual) null else id }

    fun onReintentar() = cargarHome()

    // ---------------- Carga de datos ----------------

    /** Consulta categorías, locales y promociones EN PARALELO. */
    private fun cargarHome() {
        viewModelScope.launch {
            dataState.value = DataState.Loading
            dataState.value = try {
                coroutineScope {
                    val categorias = async { repository.getCategorias() }
                    val locales = async { repository.getLocales() }
                    val promociones = async { repository.getPromocionesVigentes() }
                    enriquecer(categorias.await(), locales.await(), promociones.await())
                }
            } catch (e: CancellationException) {
                throw e // nunca tragarse la cancelación de corrutinas
            } catch (e: Exception) {
                DataState.Failed("No pudimos cargar el contenido. Revisa tu conexión e inténtalo de nuevo.")
            }
        }
    }

    /** Completa nombres (categoría del local, local de la promoción) que la BD entrega como ids. */
    private fun enriquecer(
        categorias: List<Categoria>,
        locales: List<LocalGastronomico>,
        promociones: List<Promocion>
    ): DataState.Loaded {
        val nombreCategoria = categorias.associate { it.id to it.nombre }
        val localesOk = locales.map { it.copy(categoriaNombre = nombreCategoria[it.categoriaId].orEmpty()) }
        val localPorId = localesOk.associateBy { it.id }
        val promosOk = promociones.map {
            it.copy(
                localNombre = localPorId[it.localId]?.nombre.orEmpty(),
                localCategoriaId = localPorId[it.localId]?.categoriaId
            )
        }
        return DataState.Loaded(categorias, localesOk, promosOk)
    }

    // ---------------- Lógica de presentación ----------------

    private fun construirSuccess(
        data: DataState.Loaded,
        query: String,
        categoriaId: String?,
        ubicacion: UbicacionUsuario?
    ): HomeUiState.Success {
        val q = query.normalizado()

        // Distancia desde el usuario a cada local.
        val locales = data.locales.map { local ->
            val lat = local.latitud
            val lon = local.longitud
            local.copy(
                distanciaKm = if (ubicacion != null && lat != null && lon != null)
                    distanciaKm(ubicacion.latitud, ubicacion.longitud, lat, lon) else null
            )
        }

        val localesFiltrados = locales.filter { l ->
            (categoriaId == null || l.categoriaId == categoriaId) &&
                (q.isEmpty() || listOf(l.nombre, l.categoriaNombre, l.comuna).any { it.normalizado().contains(q) })
        }

        val promosFiltradas = data.promociones.filter { p ->
            (categoriaId == null || p.localCategoriaId == categoriaId) &&
                (q.isEmpty() || listOf(p.localNombre, p.titulo, p.descripcion).any { it.normalizado().contains(q) })
        }

        return HomeUiState.Success(
            categorias = data.categorias,
            categoriaSeleccionadaId = categoriaId,
            localesParaTi = localesFiltrados.sortedByDescending(::afinidad),
            promociones = promosFiltradas
        )
    }

    /**
     * Puntaje provisional para "Para ti" (60 % valoración, 40 % cercanía).
     * TODO(RF-14): incorporar preferencias del usuario e historial de interacciones.
     */
    private fun afinidad(local: LocalGastronomico): Double {
        val valoracion = local.valoracion / 5.0
        val cercania = 1.0 - ((local.distanciaKm ?: MAX_KM) / MAX_KM).coerceIn(0.0, 1.0)
        return valoracion * 0.6 + cercania * 0.4
    }

    companion object {
        private const val MAX_KM = 20.0

        val Factory = viewModelFactory {
            initializer { HomeViewModel(AppContainer.homeRepository, AppContainer.ubicacionRepository) }
        }
    }
}
