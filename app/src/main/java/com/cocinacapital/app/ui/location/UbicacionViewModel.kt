package com.cocinacapital.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.data.location.UbicacionRepository
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class UbicacionViewModel(private val repository: UbicacionRepository) : ViewModel() {
    val uiState = repository.estado.map { it.paraInterfaz() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.estado.value.paraInterfaz())
    private val resultadosPendientes = Channel<UbicacionUiState>(Channel.BUFFERED)
    val resultados = resultadosPendientes.receiveAsFlow()
    private var consulta: Job? = null
    fun obtener() {
        if (consulta?.isActive != true) consulta = viewModelScope.launch {
            repository.obtener()
            resultadosPendientes.send(repository.estado.value.paraInterfaz())
        }
    }
    fun cancelar() { consulta?.cancel(); consulta = null }
    fun limpiar() { cancelar(); repository.limpiar() }
    fun permisoDenegado() {
        cancelar(); repository.permisoDenegado()
        resultadosPendientes.trySend(repository.estado.value.paraInterfaz())
    }
    fun validar() = repository.validarDisponibilidad()
    companion object { val Factory = viewModelFactory { initializer { UbicacionViewModel(AppContainer.ubicacionRepository) } } }
}
