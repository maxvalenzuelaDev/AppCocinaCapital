package com.cocinacapital.app.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.data.repository.Interaccion
import com.cocinacapital.app.data.remote.reconexiones
import com.cocinacapital.app.data.repository.RegistroInteracciones
import com.cocinacapital.app.data.repository.TipoInteraccion
import com.cocinacapital.app.data.model.Categoria
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.PreferenciasUsuario
import com.cocinacapital.app.data.model.PresupuestoPersona
import com.cocinacapital.app.data.repository.HomeRepository
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.ui.home.UbicacionUsuario
import com.cocinacapital.app.util.distanciaKm
import com.cocinacapital.app.util.normalizado
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FiltrosExplorar(val texto: String = "", val categoriaId: String? = null, val radioKm: Int = 5,
    val zonaManual: Boolean = false, val seleccionadoId: String? = null, val soloPreferidas: Boolean = false,
    val presupuesto: PresupuestoPersona? = null) {
    val cantidadActivos: Int get() = listOf(texto.isNotBlank(), categoriaId != null, radioKm != 5, soloPreferidas, presupuesto?.sinLimite == false).count { it }
}

sealed interface ExplorarUiState {
    data object Loading : ExplorarUiState
    data object Error : ExplorarUiState
    data class Success(val categorias: List<Categoria>, val locales: List<LocalGastronomico>,
        val origen: UbicacionUsuario?, val filtros: FiltrosExplorar, val soloPreferidas: Boolean, val preferidasDisponibles: Boolean) : ExplorarUiState {
        val seleccionado get() = locales.firstOrNull { it.id == filtros.seleccionadoId }
    }
}

/** Mismo conjunto ordenado para marcadores y tarjetas; nunca hay cercanía sin un origen. */
fun localesCercanos(locales: List<LocalGastronomico>, filtros: FiltrosExplorar, origen: UbicacionUsuario?,
    preferencias: PreferenciasUsuario): List<LocalGastronomico> {
    if (origen == null) return emptyList()
    val q = filtros.texto.normalizado()
    return locales.asSequence().filter {
        (filtros.categoriaId == null || it.categoriaId == filtros.categoriaId) &&
            (!filtros.soloPreferidas || it.categoriaId in preferencias.categoriasIds) &&
            (filtros.presupuesto == null || filtros.presupuesto.sinLimite ||
                it.ticketPromedioPersona?.let(filtros.presupuesto::incluye) == true) &&
            (q.isBlank() || listOf(it.nombre, it.comuna, it.categoriaNombre).any { text -> text.normalizado().contains(q) })
    }.map { local ->
        val lat = local.latitud; val lon = local.longitud
        local.copy(distanciaKm = if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
            distanciaKm(origen.latitud, origen.longitud, lat, lon) else null)
    }.filter { it.distanciaKm?.let { d -> d.isFinite() && d <= filtros.radioKm } == true }
        .sortedWith(compareBy<LocalGastronomico> { it.distanciaKm }.thenBy { it.nombre }).toList()
}

class ExplorarViewModel(private val catalogo: HomeRepository, private val ubicacion: UbicacionRepository,
    preferencias: Flow<PreferenciasUsuario>, private val saved: SavedStateHandle,
    private val interacciones: RegistroInteracciones = RegistroInteracciones.Ninguno,
    reconexiones: Flow<Unit> = emptyFlow()) : ViewModel() {
    private sealed interface Catalogo {
        data object Cargando : Catalogo
        data object Error : Catalogo
        data class Listo(val categorias: List<Categoria>, val locales: List<LocalGastronomico>) : Catalogo
    }
    private val datos = MutableStateFlow<Catalogo>(Catalogo.Cargando)
    private val filtros = MutableStateFlow(FiltrosExplorar(saved["texto"] ?: "", saved["categoria"],
        (saved.get<Int>("radio") ?: 5).takeIf { it in listOf(5, 10, 20) } ?: 5,
        saved["manual"] ?: false, saved["seleccionado"], saved["preferidas"] ?: false,
        runCatching { PresupuestoPersona(saved.get<Long>("presupuesto_desde") ?: 0L, saved["presupuesto_hasta"]) }
            .getOrNull()?.takeUnless { it.sinLimite }))
    val filtrosBusqueda: StateFlow<FiltrosExplorar> = filtros.asStateFlow()
    private var carga: Job? = null
    val uiState: StateFlow<ExplorarUiState> = combine(datos, filtros, ubicacion.posicion, preferencias) { data, filter, gps, prefs ->
        when (data) {
            Catalogo.Cargando -> ExplorarUiState.Loading
            Catalogo.Error -> ExplorarUiState.Error
            is Catalogo.Listo -> {
                val origin = if (filter.zonaManual) UbicacionUsuario("Viña del Mar", -33.0188, -71.5570) else gps
                ExplorarUiState.Success(data.categorias, localesCercanos(data.locales, filter, origin, prefs), origin, filter, filter.soloPreferidas, prefs.categoriasIds.isNotEmpty())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplorarUiState.Loading)

    init {
        saved.remove<String>("precio"); recargar()
        // HU-35: si el catálogo no se pudo cargar, se reintenta solo al volver la conexión.
        viewModelScope.launch { reconexiones.collect { if (datos.value is Catalogo.Error) recargar() } }
    }
    fun recargar() {
        carga?.cancel()
        carga = viewModelScope.launch {
            datos.value = Catalogo.Cargando
            datos.value = try {
                coroutineScope {
                    val categorias = async { catalogo.getCategorias() }
                    val locales = async { catalogo.getLocales() }
                    val cats = categorias.await()
                    val nombres = cats.associate { it.id to it.nombre }
                    Catalogo.Listo(cats, locales.await().map { it.copy(categoriaNombre = nombres[it.categoriaId].orEmpty()) })
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Catalogo.Error }
        }
    }
    fun texto(value: String) {
        cambiar { it.copy(texto = value, seleccionadoId = null) }
        registrarBusqueda(value)
    }

    /**
     * HU-20: una búsqueda se registra cuando el texto deja de cambiar, no por cada letra. Se ignoran
     * los textos muy cortos y repetir la misma búsqueda.
     */
    private var busquedaPendiente: Job? = null
    private var ultimaBusqueda = ""
    private fun registrarBusqueda(texto: String) {
        busquedaPendiente?.cancel()
        val termino = texto.trim()
        if (termino.length < LARGO_MINIMO_BUSQUEDA || termino.equals(ultimaBusqueda, ignoreCase = true)) return
        busquedaPendiente = viewModelScope.launch {
            delay(ESPERA_BUSQUEDA_MS)
            ultimaBusqueda = termino
            interacciones.registrar(Interaccion(TipoInteraccion.BUSQUEDA, termino = termino))
        }
    }
    /** Inicio aporta su categoría rápida; la consulta y los filtros avanzados siguen siendo de Explorar. */
    fun iniciarBusquedaDesdeHome(categoriaId: String?) = cambiar { it.copy(categoriaId = categoriaId, seleccionadoId = null) }
    fun categoria(id: String?) = cambiar { it.copy(categoriaId = if (it.categoriaId == id) null else id, seleccionadoId = null) }
    fun radio(km: Int) { require(km in listOf(5, 10, 20)); cambiar { it.copy(radioKm = km, seleccionadoId = null) } }
    fun preferidas() = cambiar { it.copy(soloPreferidas = !it.soloPreferidas, seleccionadoId = null) }
    fun presupuesto(value: PresupuestoPersona?) = cambiar { it.copy(presupuesto = value?.takeUnless { p -> p.sinLimite }, seleccionadoId = null) }
    fun aplicarFiltros(value: FiltrosExplorar) {
        require(value.radioKm in listOf(5, 10, 20))
        cambiar { value.copy(zonaManual = it.zonaManual, seleccionadoId = null, presupuesto = value.presupuesto?.takeUnless { p -> p.sinLimite }) }
    }
    fun seleccionar(id: String?) = cambiar { it.copy(seleccionadoId = id) }
    fun elegirVina() = cambiar { it.copy(zonaManual = true, seleccionadoId = null) }
    fun usarGps() = cambiar { it.copy(zonaManual = false, seleccionadoId = null) }
    fun limpiarFiltros() = cambiar { FiltrosExplorar(zonaManual = it.zonaManual) }
    private fun cambiar(transform: (FiltrosExplorar) -> FiltrosExplorar) {
        val next = transform(filtros.value)
        saved["texto"] = next.texto; saved["categoria"] = next.categoriaId; saved["radio"] = next.radioKm
        saved["preferidas"] = next.soloPreferidas; saved["manual"] = next.zonaManual; saved["seleccionado"] = next.seleccionadoId
        saved["presupuesto_desde"] = next.presupuesto?.minimo
        saved["presupuesto_hasta"] = next.presupuesto?.maximo
        filtros.value = next
    }
    companion object {
        private const val LARGO_MINIMO_BUSQUEDA = 3
        private const val ESPERA_BUSQUEDA_MS = 1_500L

        val Factory = viewModelFactory { initializer {
            ExplorarViewModel(AppContainer.homeRepository, AppContainer.ubicacionRepository,
                AppContainer.preferenciasRepository.observar(), createSavedStateHandle(), AppContainer.registroInteracciones,
                AppContainer.conectividad.reconexiones())
        } }
    }
}
