package com.cocinacapital.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.data.repository.HistorialInteracciones
import com.cocinacapital.app.data.model.ActividadReciente
import com.cocinacapital.app.data.model.PerfilRecomendacion
import com.cocinacapital.app.data.model.PreferenciasUsuario
import com.cocinacapital.app.data.model.Recomendador
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.util.distanciaKm
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
    private val ubicaciones: com.cocinacapital.app.data.location.UbicacionRepository,
    preferencias: Flow<PreferenciasUsuario> = flowOf(PreferenciasUsuario()),
    private val historial: HistorialInteracciones = HistorialInteracciones.Vacio
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

    val ubicacion = ubicaciones.posicion

    /** HU-21: actividad reciente en Supabase; se vuelve a leer al volver al Inicio. */
    private val actividad = MutableStateFlow<List<ActividadReciente>>(emptyList())

    /**
     * Gustos elegidos en Perfil más la actividad, que solo se usa con el consentimiento activo
     * (HU-03): al revocarlo, las recomendaciones dejan de considerarla aunque siga guardada.
     */
    private val perfil = combine(preferencias, actividad) { prefs, recientes ->
        PerfilRecomendacion(
            categoriasPreferidas = prefs.categoriasIds,
            rangoPrecio = prefs.rangoPrecio,
            actividad = if (prefs.consentimiento) recientes else emptyList()
        )
    }

    /** Estado único que observa la pantalla. */
    val uiState: StateFlow<HomeUiState> = combine(
        dataState, categoriaSeleccionadaId, ubicacion, perfil
    ) { data, categoriaId, ubicacion, perfil ->
        when (data) {
            DataState.Loading -> HomeUiState.Loading
            DataState.Failed -> HomeUiState.Error(com.cocinacapital.app.R.string.home_error)
            is DataState.Loaded -> construirSuccess(data, categoriaId, ubicacion, perfil)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000), // sobrevive a rotaciones
        initialValue = HomeUiState.Loading
    )

    init {
        cargarHome()
        refrescarActividad()
    }

    /** Al volver al Inicio: lo que vio o canjeó cambia sus recomendaciones. Sin red se conserva lo anterior. */
    fun refrescarActividad() {
        viewModelScope.launch {
            try {
                actividad.value = historial.recientes()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
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
        ubicacion: UbicacionUsuario?,
        perfil: PerfilRecomendacion
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

        // HU-21: las recomendadas se muestran aparte y no se repiten en la lista general.
        val paraTi = Recomendador.promocionesRecomendadas(promosFiltradas, locales, perfil)
        val resto = promosFiltradas.filterNot { promo -> paraTi.any { it.id == promo.id } }

        return HomeUiState.Success(
            categorias = data.categorias,
            categoriaSeleccionadaId = categoriaId,
            localesParaTi = Recomendador.ordenarLocales(localesFiltrados, perfil),
            promociones = if (gps != null) resto.sortedBy { it.distanciaKm ?: Double.POSITIVE_INFINITY } else resto,
            tieneUbicacion = gps != null,
            promocionesParaTi = paraTi,
            personalizado = perfil.personalizado
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    AppContainer.homeRepository, AppContainer.ubicacionRepository,
                    AppContainer.preferenciasRepository.observar(), AppContainer.historialInteracciones
                )
            }
        }
    }
}
