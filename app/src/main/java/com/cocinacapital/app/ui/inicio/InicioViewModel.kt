package com.cocinacapital.app.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cocinacapital.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface InicioUiState {
    data object Loading : InicioUiState
    data object Success : InicioUiState
    data object Error : InicioUiState
}

class InicioViewModel(private val preparar: suspend () -> Unit) : ViewModel() {
    private val mutable = MutableStateFlow<InicioUiState>(InicioUiState.Loading)
    val uiState = mutable.asStateFlow()
    private var carga: Job? = null
    init { reintentar() }
    fun reintentar() {
        if (carga?.isActive == true) return
        mutable.value = InicioUiState.Loading
        carga = viewModelScope.launch {
            mutable.value = try {
                withTimeout(20_000) { preparar() }
                InicioUiState.Success
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                InicioUiState.Error
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { InicioUiState.Error }
        }
    }
    companion object {
        val Factory = viewModelFactory {
            initializer { InicioViewModel { withContext(Dispatchers.IO) { AppContainer.prepararInicio() } } }
        }
    }
}
