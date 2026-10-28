package com.cocinacapital.app.data.location

import com.cocinacapital.app.ui.home.UbicacionUsuario
import com.cocinacapital.location.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex

data class EstadoUbicacion(
    val posicion: UserPosition? = null,
    val cargando: Boolean = false,
    val problema: LocationProblem? = null
)

interface UbicacionRepository {
    val estado: StateFlow<EstadoUbicacion>
    val posicion: Flow<UbicacionUsuario?>
    suspend fun obtener()
    fun limpiar()
    fun permisoDenegado()
    fun validarDisponibilidad()
}

/** Estado compartido solo en memoria. Una consulta anterior no puede restaurar una sesión borrada. */
class AndroidUbicacionRepository(private val source: LocationSource, clock: () -> Long) : UbicacionRepository {
    private val delegate = LocationRepository(source, clock)
    private val mutable = MutableStateFlow(EstadoUbicacion())
    private val consulta = Mutex()
    private var generacion = 0L
    override val estado = mutable.asStateFlow()
    override val posicion = estado.map { state -> state.posicion?.let {
        UbicacionUsuario("", it.latitude, it.longitude)
    } }.distinctUntilChanged()

    override suspend fun obtener() {
        if (!consulta.tryLock()) return
        val version = generacion
        mutable.value = mutable.value.copy(cargando = true, problema = null)
        try {
            val result = delegate.currentPosition()
            if (version != generacion) return
            mutable.value = when (result) {
                is LocationResult.Success -> EstadoUbicacion(posicion = result.position)
                is LocationResult.Failure -> EstadoUbicacion(problema = result.problem)
            }
        } finally {
            if (version == generacion) mutable.value = mutable.value.copy(cargando = false)
            consulta.unlock()
        }
    }

    override fun limpiar() { generacion++; mutable.value = EstadoUbicacion() }

    override fun permisoDenegado() {
        limpiar()
        mutable.value = EstadoUbicacion(problema = LocationProblem.PERMISSION_REQUIRED)
    }

    override fun validarDisponibilidad() {
        if (source.access() == LocationAccess.NONE) invalidar(LocationProblem.PERMISSION_REQUIRED)
        else if (!source.locationEnabled()) invalidar(LocationProblem.LOCATION_DISABLED)
        else if (estado.value.problema == LocationProblem.PERMISSION_REQUIRED || estado.value.problema == LocationProblem.LOCATION_DISABLED)
            mutable.value = mutable.value.copy(problema = null)
    }

    private fun invalidar(problema: LocationProblem) {
        val solicitado = estado.value.posicion != null || estado.value.problema != null || estado.value.cargando
        limpiar()
        if (solicitado) mutable.value = EstadoUbicacion(problema = problema)
    }
}
