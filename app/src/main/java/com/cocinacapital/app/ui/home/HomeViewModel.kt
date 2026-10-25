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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del Home.
 *
 * Idea clave: se descargan los datos UNA vez (estado "crudo" [DataState]) y luego
 * [uiState] se recalcula al cambiar la categoría o la ubicación compartida.
 * La búsqueda completa y sus filtros se abren en Explorar.
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
        data object Failed : DataState
    }

    private val dataState = MutableStateFlow<DataState>(DataState.Loading)
    private val categoriaSeleccionadaId = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun onSearchQueryChange(texto: String) { _searchQuery.value = texto }

    val ubicacion = ubicaciones.posicion

    /** Estado único que observa la pantalla. */
    val uiState: StateFlow<HomeUiState> = combine(
        dataState, categoriaSeleccionadaId, ubicacion, _searchQuery
    ) { data, categoriaId, ubicacion, query ->
        when (data) {
            DataState.Loading -> HomeUiState.Loading
            DataState.Failed -> HomeUiState.Error(com.cocinacapital.app.R.string.home_error)
            is DataState.Loaded -> construirSuccess(data, categoriaId, ubicacion).let { contenido ->
                val texto = query.normalizado()
                contenido.copy(
                    localesParaTi = contenido.localesParaTi.filter { texto.isBlank() || listOf(it.nombre, it.categoriaNombre, it.comuna).any { dato -> dato.normalizado().contains(texto) } },
                    promociones = contenido.promociones.filter { texto.isBlank() || listOf(it.titulo, it.localNombre, it.descripcion).any { dato -> dato.normalizado().contains(texto) } }
                )
            }
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
                DataState.Failed
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
        val promosOk = promociones.mapNotNull { promo ->
            val local = localPorId[promo.localId] ?: return@mapNotNull null
            promo.copy(
                localNombre = local.nombre,
                localCategoriaId = local.categoriaId
            )
        }
        return DataState.Loaded(categorias, localesOk, promosOk)
    }

    // ---------------- Lógica de presentación ----------------

    private fun construirSuccess(
        data: DataState.Loaded,
        categoriaId: String?,
        ubicacion: UbicacionUsuario?
    ): HomeUiState.Success {
        val gps = ubicacion?.takeIf { it.latitud.isFinite() && it.longitud.isFinite() &&
            it.latitud in -90.0..90.0 && it.longitud in -180.0..180.0 }

        // Distancia desde el usuario a cada local.
        val locales = data.locales.map { local ->
            val lat = local.latitud
            val lon = local.longitud
            local.copy(
                distanciaKm = if (gps != null && lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                    lat in -90.0..90.0 && lon in -180.0..180.0)
                    distanciaKm(gps.latitud, gps.longitud, lat, lon) else null
            )
        }

        val localesFiltrados = locales.filter { l ->
            categoriaId == null || l.categoriaId == categoriaId
        }

        val distancias = locales.associate { it.id to it.distanciaKm }
        val promosFiltradas = data.promociones.filter { categoriaId == null || it.localCategoriaId == categoriaId }
            .map { it.copy(distanciaKm = distancias[it.localId]) }

        return HomeUiState.Success(
            categorias = data.categorias,
            categoriaSeleccionadaId = categoriaId,
            localesParaTi = localesFiltrados.sortedByDescending(::afinidad),
            promociones = if (gps != null) promosFiltradas.sortedBy { it.distanciaKm ?: Double.POSITIVE_INFINITY } else promosFiltradas,
            tieneUbicacion = gps != null
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
